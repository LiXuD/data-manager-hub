package com.dataplatform.governance.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.governance.api.dto.AlertRecordCreateDTO;
import com.dataplatform.governance.api.dto.AlertRuleDTO;
import com.dataplatform.governance.api.dto.DataLineageDTO;
import com.dataplatform.governance.api.dto.QualityScoreDTO;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "data-platform-governance", contextId = "governanceFeignClient", path = "/governance")
public interface GovernanceFeignClient {

    @GetMapping("/alert-rules/{id}")
    Result<AlertRuleDTO> getAlertRule(@PathVariable("id") Long id);

    @GetMapping("/quality/check")
    Result<QualityScoreDTO> checkQuality(@RequestParam("dataType") String dataType,
                                         @RequestParam("dataId") Long dataId);

    @GetMapping("/lineage/upstream")
    Result<List<DataLineageDTO>> getUpstreamLineage(@RequestParam("type") String type,
                                                    @RequestParam("id") Long id);

    @PostMapping("/alert-records")
    Result<Void> createAlertRecord(@RequestBody AlertRecordCreateDTO dto);
}
