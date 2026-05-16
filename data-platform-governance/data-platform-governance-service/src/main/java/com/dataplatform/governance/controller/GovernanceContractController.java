package com.dataplatform.governance.controller;

import com.dataplatform.api.Result;
import com.dataplatform.governance.api.dto.AlertRuleDTO;
import com.dataplatform.governance.api.dto.DataLineageDTO;
import com.dataplatform.governance.api.dto.QualityScoreDTO;
import com.dataplatform.governance.api.feign.GovernanceFeignClient;
import com.dataplatform.governance.monitor.entity.AlertRule;
import com.dataplatform.governance.monitor.service.AlertService;
import com.dataplatform.governance.quality.entity.QualityScore;
import com.dataplatform.governance.quality.service.QualityService;
import com.dataplatform.governance.trace.entity.DataLineage;
import com.dataplatform.governance.trace.service.DataLineageService;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GovernanceContractController implements GovernanceFeignClient {

    private final AlertService alertService;
    private final QualityService qualityService;
    private final DataLineageService dataLineageService;

    public GovernanceContractController(AlertService alertService,
                                        QualityService qualityService,
                                        DataLineageService dataLineageService) {
        this.alertService = alertService;
        this.qualityService = qualityService;
        this.dataLineageService = dataLineageService;
    }

    @Override
    public Result<AlertRuleDTO> getAlertRule(Long id) {
        return Result.success(toAlertRuleDTO(alertService.getRuleById(id)));
    }

    @Override
    public Result<QualityScoreDTO> checkQuality(String dataType, Long dataId) {
        return Result.success(toQualityScoreDTO(qualityService.checkQuality(dataType, dataId)));
    }

    @Override
    public Result<List<DataLineageDTO>> getUpstreamLineage(String type, Long id) {
        List<DataLineageDTO> result = dataLineageService.getUpstream(type, id)
                .stream()
                .map(this::toDataLineageDTO)
                .toList();
        return Result.success(result);
    }

    private AlertRuleDTO toAlertRuleDTO(AlertRule source) {
        if (source == null) {
            return null;
        }
        AlertRuleDTO target = new AlertRuleDTO();
        BeanUtils.copyProperties(source, target);
        if (source.getStatus() != null) {
            target.setStatus(source.getStatus().name());
        }
        return target;
    }

    private QualityScoreDTO toQualityScoreDTO(QualityScore source) {
        if (source == null) {
            return null;
        }
        QualityScoreDTO target = new QualityScoreDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }

    private DataLineageDTO toDataLineageDTO(DataLineage source) {
        DataLineageDTO target = new DataLineageDTO();
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
