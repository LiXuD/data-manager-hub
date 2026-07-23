package com.dataplatform.masterdata.interface_.service;

import com.dataplatform.masterdata.interface_.api.dto.InterfaceContractDTO;

/** 接口调用契约的唯一写入入口。 */
public interface InterfaceContractService {
    InterfaceContractDTO getContract(Long interfaceId);

    InterfaceContractDTO saveContract(Long interfaceId, InterfaceContractDTO contract);

    InterfaceContractDTO refreshSnapshots(Long interfaceId);
}
