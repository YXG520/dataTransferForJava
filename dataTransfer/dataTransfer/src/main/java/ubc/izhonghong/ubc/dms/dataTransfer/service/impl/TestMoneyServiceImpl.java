package ubc.izhonghong.ubc.dms.dataTransfer.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import ubc.izhonghong.ubc.dms.dataTransfer.dao.MoneyMapper;
import ubc.izhonghong.ubc.dms.dataTransfer.pojo.MoneyPo;
import ubc.izhonghong.ubc.dms.dataTransfer.service.MoneyService;


@Service
@DS("test")
public class TestMoneyServiceImpl extends ServiceImpl<MoneyMapper, MoneyPo> implements MoneyService {



}
