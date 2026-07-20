#!/usr/bin/env bash
# arch-scan.sh — 五域架构边界自动扫描
# 检查项:
#   1. service 不依赖其他域 service
#   2. api 不引入重型依赖 (MyBatis/DB/Redis/Nacos/SpringBoot)
#   3. 禁止全包扫描 @SpringBootApplication(scanBasePackages = "com.dataplatform")
#   4. 禁止跨域 import 他域 entity/mapper/service/controller
#   5. 旧小服务不在根 POM modules 中
#   6. Feign 仅用于带作用域保护的内部契约
#   7. 各域只读取自己拥有的数据表
#   8. Billing 不通过 Kafka 消费 Access 调用记录

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
VIOLATIONS=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[PASS]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
fail()  { echo -e "${RED}[FAIL]${NC} $1"; VIOLATIONS=$((VIOLATIONS + 1)); }

# 五域定义
DOMAINS=(masterdata access billing identity governance)
OLD_SERVICES=(vendor caller call tenant iam monitor log quality trace security graylog interface)

echo "============================================"
echo "  五域架构边界扫描"
echo "============================================"
echo ""

# -------------------------------------------------------
# 检查 1: service POM 不依赖其他域 service
# -------------------------------------------------------
echo "--- 检查 1: 跨域 service 依赖 ---"
for domain in "${DOMAINS[@]}"; do
    service_pom="$PROJECT_ROOT/data-platform-${domain}/data-platform-${domain}-service/pom.xml"
    if [ ! -f "$service_pom" ]; then
        warn "找不到 $service_pom，跳过"
        continue
    fi
    for other in "${DOMAINS[@]}"; do
        if [ "$domain" = "$other" ]; then
            continue
        fi
        # 匹配 <artifactId>data-platform-{other}-service</artifactId>
        if grep -q "data-platform-${other}-service" "$service_pom"; then
            fail "[跨域依赖] data-platform-${domain}-service 依赖了 data-platform-${other}-service"
        fi
    done
    # 检查是否依赖旧服务
    for old_svc in "${OLD_SERVICES[@]}"; do
        if grep -q "data-platform-${old_svc}" "$service_pom" 2>/dev/null; then
            fail "[旧服务残留] data-platform-${domain}-service 仍依赖 data-platform-${old_svc}"
        fi
    done
done
echo ""

# -------------------------------------------------------
# 检查 2: api 模块不引入重型依赖
# -------------------------------------------------------
echo "--- 检查 2: api 模块重型依赖 ---"
HEAVY_DEPS="mybatis mybatis-plus mysql postgresql druid hikari redis redisson nacos spring-boot-starter-data spring-boot-starter-web spring-boot-starter-redis spring-boot-starter-cache"
for domain in "${DOMAINS[@]}"; do
    api_pom="$PROJECT_ROOT/data-platform-${domain}/data-platform-${domain}-api/pom.xml"
    if [ ! -f "$api_pom" ]; then
        warn "找不到 $api_pom，跳过"
        continue
    fi
    for dep in $HEAVY_DEPS; do
        if grep -qi "$dep" "$api_pom"; then
            fail "[api过重] data-platform-${domain}-api 引入了重型依赖: $dep"
        fi
    done
done
echo ""

# -------------------------------------------------------
# 检查 3: 禁止全包扫描
# -------------------------------------------------------
echo "--- 检查 3: 全包扫描 ---"
found_full_scan=false
for domain in "${DOMAINS[@]}"; do
    service_dir="$PROJECT_ROOT/data-platform-${domain}/data-platform-${domain}-service/src"
    if [ ! -d "$service_dir" ]; then
        continue
    fi
    # 查找 scanBasePackages = "com.dataplatform" (无子包限定)
    if grep -rn 'scanBasePackages.*=.*"com\.dataplatform"' "$service_dir" --include="*.java" 2>/dev/null | grep -v '"com\.dataplatform\.' ; then
        fail "[全包扫描] 发现 scanBasePackages = \"com.dataplatform\" (应指定子包)"
        found_full_scan=true
    fi
done
if [ "$found_full_scan" = false ]; then
    info "无全包扫描问题"
fi
echo ""

# -------------------------------------------------------
# 检查 4: 跨域 import
# -------------------------------------------------------
echo "--- 检查 4: 跨域 import ---"
for domain in "${DOMAINS[@]}"; do
    service_dir="$PROJECT_ROOT/data-platform-${domain}/data-platform-${domain}-service/src"
    if [ ! -d "$service_dir" ]; then
        continue
    fi
    for other in "${DOMAINS[@]}"; do
        if [ "$domain" = "$other" ]; then
            continue
        fi
        # 检查是否 import 了他域的 entity/mapper/service/controller
        for pkg in entity mapper service controller; do
            imports=$(grep -rn "import com\.dataplatform\.${other}\..*\.${pkg}\." "$service_dir" --include="*.java" 2>/dev/null || true)
            if [ -n "$imports" ]; then
                while IFS= read -r line; do
                    file=$(echo "$line" | cut -d: -f1 | sed "s|$PROJECT_ROOT/||")
                    fail "[跨域import] $file 引用了 ${other} 域的 ${pkg} 包"
                done <<< "$imports"
            fi
        done
    done
done
echo ""

# -------------------------------------------------------
# 检查 5: 旧小服务不在根 POM modules 中
# -------------------------------------------------------
echo "--- 检查 5: 旧服务模块残留 ---"
root_pom="$PROJECT_ROOT/pom.xml"
for old_svc in "${OLD_SERVICES[@]}"; do
    if grep -q "data-platform-${old_svc}" "$root_pom"; then
        fail "[旧模块] 根 POM 仍包含 data-platform-${old_svc}"
    fi
done

# 检查旧目录是否存在
for old_svc in "${OLD_SERVICES[@]}"; do
    old_dir="$PROJECT_ROOT/data-platform-${old_svc}"
    if [ -d "$old_dir" ]; then
        warn "[旧目录] data-platform-${old_svc}/ 目录仍存在 (可考虑归档)"
    fi
done
echo ""

# -------------------------------------------------------
# 检查 6: Feign 仅用于内部契约，且显式注册
# -------------------------------------------------------
echo "--- 检查 6: Feign 内部契约与显式注册 ---"
while IFS= read -r file; do
    if ! rg -q 'path\s*=\s*"/internal/' "$file"; then
        fail "[公共Feign] ${file#"$PROJECT_ROOT/"} 未使用 /internal/ 契约路径"
    fi
    if ! rg -q '@InternalFeignContract' "$file"; then
        fail "[内部Feign无标记] ${file#"$PROJECT_ROOT/"} 缺少 @InternalFeignContract"
    fi
done < <(rg -l '@FeignClient' "$PROJECT_ROOT"/data-platform-*/data-platform-*-api/src/main/java --glob '*.java' || true)

while IFS= read -r file; do
    if ! rg -q 'clients\s*=' "$file"; then
        fail "[隐式Feign扫描] ${file#"$PROJECT_ROOT/"} 必须通过 clients 显式注册契约"
    fi
done < <(rg -l '@EnableFeignClients' "$PROJECT_ROOT"/data-platform-*/data-platform-*-service/src/main/java --glob '*.java' || true)

while IFS= read -r file; do
    if ! rg -q '@InternalScope' "$file"; then
        fail "[内部接口无作用域] ${file#"$PROJECT_ROOT/"} 缺少 @InternalScope"
    fi
done < <(rg -l '@RequestMapping\("/internal/' "$PROJECT_ROOT"/data-platform-*/data-platform-*-service/src/main/java --glob '*.java' || true)
echo ""

# -------------------------------------------------------
# 检查 7: 禁止跨域读取领域表
# -------------------------------------------------------
echo "--- 检查 7: 领域数据所有权 ---"
for domain in masterdata billing identity governance; do
    service_src="$PROJECT_ROOT/data-platform-${domain}/data-platform-${domain}-service/src"
    if rg -n -i --pcre2 '\b(?:from|join|update|into)\s+[`"]?call_record\b' \
        "$service_src" --glob '*.{java,xml,sql}' 2>/dev/null; then
        fail "[跨域读表] ${domain} 域直接引用了 Access 域 call_record 表"
    fi
done

for domain in access billing identity governance; do
    service_src="$PROJECT_ROOT/data-platform-${domain}/data-platform-${domain}-service/src"
    if rg -n -i --pcre2 '\b(?:from|join|update|into)\s+[`"]?data_type\b' \
        "$service_src" --glob '*.{java,xml,sql}' 2>/dev/null; then
        fail "[跨域读表] ${domain} 域直接引用了 Masterdata 域 data_type 表"
    fi
done
echo ""

# -------------------------------------------------------
# 检查 8: Billing 不消费 Access 调用记录事件
# -------------------------------------------------------
echo "--- 检查 8: Billing 跨域事件 ---"
billing_src="$PROJECT_ROOT/data-platform-billing/data-platform-billing-service/src"
while IFS= read -r file; do
    if rg -q -i 'call[-_.]?record' "$file"; then
        fail "[跨域Kafka] ${file#"$PROJECT_ROOT/"} 消费了 Access 调用记录事件"
    fi
done < <(rg -l '@KafkaListener' "$billing_src" --glob '*.java' || true)
echo ""

# -------------------------------------------------------
# 汇总
# -------------------------------------------------------
echo "============================================"
if [ "$VIOLATIONS" -eq 0 ]; then
    echo -e "${GREEN}扫描通过，未发现架构违规${NC}"
else
    echo -e "${RED}发现 ${VIOLATIONS} 项架构违规${NC}"
fi
echo "============================================"

exit "$VIOLATIONS"
