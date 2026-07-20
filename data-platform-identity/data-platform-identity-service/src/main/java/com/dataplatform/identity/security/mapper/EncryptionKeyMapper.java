package com.dataplatform.identity.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataplatform.identity.security.entity.EncryptionKey;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EncryptionKeyMapper extends BaseMapper<EncryptionKey> {
}
