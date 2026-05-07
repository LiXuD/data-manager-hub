package com.dataplatform.interface_.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataplatform.interface_.entity.InterfaceParam;

import java.util.List;

/**
 * 接口参数定义服务
 */
public interface InterfaceParamService extends IService<InterfaceParam> {

    /**
     * 按接口ID查询参数列表，按sort排序
     */
    List<InterfaceParam> listByInterfaceId(Long interfaceId);

    /**
     * 批量保存接口参数（先删后增）
     */
    void saveParams(Long interfaceId, List<InterfaceParam> params);
}
