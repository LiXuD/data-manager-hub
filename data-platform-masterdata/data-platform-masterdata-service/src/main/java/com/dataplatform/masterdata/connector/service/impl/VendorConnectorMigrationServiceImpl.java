package com.dataplatform.masterdata.connector.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataplatform.masterdata.connector.api.dto.VendorConnectorMigrationDTO;
import com.dataplatform.masterdata.connector.entity.VendorConnectorMigration;
import com.dataplatform.masterdata.connector.mapper.VendorConnectorMigrationMapper;
import com.dataplatform.masterdata.connector.service.VendorConnectorMigrationService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Read-only history for the completed LEGACY-to-plugin migration program. */
@Service
public class VendorConnectorMigrationServiceImpl implements VendorConnectorMigrationService {

    private final VendorConnectorMigrationMapper migrationMapper;

    public VendorConnectorMigrationServiceImpl(VendorConnectorMigrationMapper migrationMapper) {
        this.migrationMapper = migrationMapper;
    }

    @Override
    public List<VendorConnectorMigrationDTO> list(String state) {
        return migrationMapper.selectList(new LambdaQueryWrapper<VendorConnectorMigration>()
                        .eq(StringUtils.hasText(state), VendorConnectorMigration::getState,
                                StringUtils.hasText(state) ? state.toUpperCase(Locale.ROOT) : null)
                        .orderByAsc(VendorConnectorMigration::getVendorConfigId))
                .stream().map(this::toDto).toList();
    }

    private VendorConnectorMigrationDTO toDto(VendorConnectorMigration value) {
        return new VendorConnectorMigrationDTO(value.getId(), value.getVendorConfigId(), value.getVendorId(),
                value.getInterfaceId(), value.getState(), value.getRecordVersion(), value.getSourceConfigHash(),
                value.getDraftId(), value.getDraftVersion(), value.getDraftSnapshotHash(),
                value.getPublishedConnectorVersionId(), value.getPublishedVersionNo(), value.getPreviousRuntimeMode(),
                value.getPreviousActiveConnectorVersionId(), value.getPreviousConnectorVersion(),
                value.getMinimumObservationMinutes(), value.getMinimumCalls(), value.getMaximumErrorRate(),
                value.getMaximumP95DurationMs(), value.getMinimumBillingCoverageRate(),
                value.getObservationStartedAt(), value.getObservationEligibleAt(), value.getObservedCalls(),
                value.getObservedSuccesses(), value.getObservedFailures(), value.getObservedErrorRate(),
                value.getObservedP95DurationMs(), value.getObservedCacheHits(), value.getObservedRealtimeCalls(),
                value.getObservedBillingEvents(), value.getObservedPostedBillingEvents(),
                value.getObservedBillingCoverageRate(), value.getObservedBillingAmount(),
                value.getObservationGatePassed(), value.getSafeErrorCode(), value.getSafeErrorDigest(),
                value.getCompletedAt(), value.getRolledBackAt(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
