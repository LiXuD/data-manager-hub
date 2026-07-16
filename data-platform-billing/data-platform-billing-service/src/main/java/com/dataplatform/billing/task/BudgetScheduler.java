package com.dataplatform.billing.task;

import com.dataplatform.billing.service.BudgetAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 计费域计费计算的 Budget Scheduler。
 * <p>定时任务组件，负责周期性触发后台检查或汇总流程。</p>
 */
@Component
public class BudgetScheduler {

    private static final Logger log = LoggerFactory.getLogger(BudgetScheduler.class);

    @Autowired
    private BudgetAlertService budgetAlertService;

    @Scheduled(cron = "0 0 1 1 * ?")
    public void resetMonthlyBudget() {
        log.info("开始重置月度预算...");
        budgetAlertService.resetMonthlyBudget();
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void checkBudgetStatus() {
        log.debug("检查预算状态...");
        budgetAlertService.checkAllActiveBudgets();
    }
}
