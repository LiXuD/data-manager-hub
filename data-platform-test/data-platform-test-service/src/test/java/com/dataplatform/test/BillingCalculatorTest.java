package com.dataplatform.test;

import com.dataplatform.common.billing.*;
import com.dataplatform.common.entity.unified.BillingRuleDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计费计算器测试
 */
@DisplayName("计费计算器测试")
class BillingCalculatorTest {

    private BillingRuleDO rule;

    @BeforeEach
    void setUp() {
        rule = new BillingRuleDO();
        rule.setId(1L);
        rule.setBillingType("STANDARD");
        rule.setUnitPrice(new BigDecimal("0.01"));
    }

    // ========== StandardBillingCalculator Tests ==========

    @Test
    @DisplayName("标准计费 - 正常计算")
    void testStandardCalculator_Success() {
        BillingCalculator calculator = new StandardBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 1000, 100);

        assertEquals(new BigDecimal("10.0000"), result);
    }

    @Test
    @DisplayName("标准计费 - 大量调用")
    void testStandardCalculator_LargeVolume() {
        BillingCalculator calculator = new StandardBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 1_000_000, 200);

        assertEquals(new BigDecimal("10000.0000"), result);
    }

    @Test
    @DisplayName("标准计费 - 零调用")
    void testStandardCalculator_ZeroCalls() {
        BillingCalculator calculator = new StandardBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 0, null);

        assertEquals(BigDecimal.ZERO.setScale(4), result);
    }

    @Test
    @DisplayName("标准计费 - 空规则")
    void testStandardCalculator_NullRule() {
        BillingCalculator calculator = new StandardBillingCalculator();

        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(null, 100, 100));
    }

    @Test
    @DisplayName("标准计费 - 单次调用")
    void testStandardCalculator_SingleCall() {
        BillingCalculator calculator = new StandardBillingCalculator();

        BigDecimal result = calculator.calculateSingle(rule, 150);

        assertEquals(new BigDecimal("0.0100"), result);
    }

    // ========== TieredBillingCalculator Tests ==========

    @Test
    @DisplayName("阶梯计费 - 第一阶梯(0-10万次)")
    void testTieredCalculator_Tier1() {
        BillingCalculator calculator = new TieredBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 50_000, 100);

        assertEquals(new BigDecimal("500.0000"), result);
    }

    @Test
    @DisplayName("阶梯计费 - 使用配置的9折")
    void testTieredCalculator_Tier2() {
        BillingCalculator calculator = new TieredBillingCalculator();

        rule.setDiscount(new BigDecimal("0.90"));
        BigDecimal result = calculator.calculate(rule, 200_000, 100);

        assertEquals(new BigDecimal("1800.0000"), result);
    }

    @Test
    @DisplayName("阶梯计费 - 使用配置的8折")
    void testTieredCalculator_Tier3() {
        BillingCalculator calculator = new TieredBillingCalculator();

        rule.setDiscount(new BigDecimal("0.80"));
        BigDecimal result = calculator.calculate(rule, 1_000_000, 100);

        assertEquals(new BigDecimal("8000.0000"), result);
    }

    @Test
    @DisplayName("阶梯计费 - 使用规则折扣")
    void testTieredCalculator_RuleDiscount() {
        rule.setDiscount(new BigDecimal("0.70"));
        BillingCalculator calculator = new TieredBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 100_000, 100);

        assertEquals(new BigDecimal("700.0000"), result);
    }

    @Test
    @DisplayName("阶梯计费 - 单次调用")
    void testTieredCalculator_SingleCall() {
        BillingCalculator calculator = new TieredBillingCalculator();

        BigDecimal result = calculator.calculateSingle(rule, 150);

        assertEquals(new BigDecimal("0.0100"), result);
    }

    @Test
    @DisplayName("阶梯计费 - 单次调用带折扣")
    void testTieredCalculator_SingleCallWithDiscount() {
        rule.setDiscount(new BigDecimal("0.90"));
        BillingCalculator calculator = new TieredBillingCalculator();

        BigDecimal result = calculator.calculateSingle(rule, 150);

        assertEquals(new BigDecimal("0.0090"), result);
    }

    // ========== DynamicBillingCalculator Tests ==========

    @Test
    @DisplayName("动态计费 - 正常响应时间")
    void testDynamicCalculator_NormalLatency() {
        BillingCalculator calculator = new DynamicBillingCalculator();
        rule.setSlaThreshold(2000);
        rule.setCompensationRate(new BigDecimal("0.05"));

        BigDecimal result = calculator.calculate(rule, 100, 500);

        assertEquals(new BigDecimal("1.0000"), result);
    }

    @Test
    @DisplayName("动态计费 - 超过SLA阈值 - 补偿")
    void testDynamicCalculator_ExceedSla() {
        BillingCalculator calculator = new DynamicBillingCalculator();
        rule.setSlaThreshold(2000);
        rule.setCompensationRate(new BigDecimal("0.05"));

        // latency 2100ms, 超过SLA 100ms, overUnits=1, compensation=0.05
        BigDecimal result = calculator.calculate(rule, 100, 2100);

        // baseAmount = 0.01 * 100 = 1.0
        // compensationFactor = 1 - 0.05 * 1 = 0.95
        assertEquals(new BigDecimal("0.9500"), result);
    }

    @Test
    @DisplayName("动态计费 - 超过SLA阈值较多 - 补偿上限")
    void testDynamicCalculator_ExceedSla_LargeOverTime() {
        BillingCalculator calculator = new DynamicBillingCalculator();
        rule.setSlaThreshold(2000);
        rule.setCompensationRate(new BigDecimal("0.10"));

        // latency 3000ms, 超过SLA 1000ms, overUnits=10, compensation=1.0 (超过100%, 补偿系数为0)
        BigDecimal result = calculator.calculate(rule, 100, 3000);

        assertEquals(new BigDecimal("0.0000"), result);
    }

    @Test
    @DisplayName("动态计费 - 单次调用")
    void testDynamicCalculator_SingleCall() {
        BillingCalculator calculator = new DynamicBillingCalculator();
        rule.setSlaThreshold(2000);
        rule.setCompensationRate(new BigDecimal("0.05"));

        BigDecimal result = calculator.calculateSingle(rule, 500);

        assertEquals(new BigDecimal("0.0100"), result);
    }

    // ========== BillingCalculatorFactory Tests ==========

    private final BillingCalculatorFactory factory = new BillingCalculatorFactory();

    @Test
    @DisplayName("工厂 - 获取标准计费器")
    void testFactory_GetStandard() {
        BillingCalculator calculator = factory.getCalculator("STANDARD");

        assertNotNull(calculator);
        assertTrue(calculator instanceof StandardBillingCalculator);
    }

    @Test
    @DisplayName("工厂 - 获取阶梯计费器")
    void testFactory_GetTiered() {
        BillingCalculator calculator = factory.getCalculator("TIERED");

        assertNotNull(calculator);
        assertTrue(calculator instanceof TieredBillingCalculator);
    }

    @Test
    @DisplayName("工厂 - 获取动态计费器")
    void testFactory_GetDynamic() {
        BillingCalculator calculator = factory.getCalculator("DYNAMIC");

        assertNotNull(calculator);
        assertTrue(calculator instanceof DynamicBillingCalculator);
    }

    @Test
    @DisplayName("工厂 - 未知类型拒绝")
    void testFactory_UnknownType() {
        assertThrows(IllegalArgumentException.class, () -> factory.getCalculator("UNKNOWN"));
    }

    @Test
    @DisplayName("工厂 - 空类型拒绝")
    void testFactory_NullType() {
        assertThrows(IllegalArgumentException.class, () -> factory.getCalculator((String) null));
    }

    // ========== 精度测试 ==========

    @Test
    @DisplayName("精度测试 - 小数计算")
    void testPrecision_DecimalCalculation() {
        rule.setUnitPrice(new BigDecimal("0.015"));
        BillingCalculator calculator = new StandardBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 333, 100);

        assertEquals(new BigDecimal("4.9950"), result);
    }

    @Test
    @DisplayName("精度测试 - 四舍五入")
    void testPrecision_Rounding() {
        rule.setUnitPrice(new BigDecimal("0.01555"));
        BillingCalculator calculator = new StandardBillingCalculator();

        BigDecimal result = calculator.calculate(rule, 100, 100);

        assertEquals(new BigDecimal("1.5550"), result);
    }
}
