package ubc.izhonghong.ubc.dms.dataTransfer.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import ubc.izhonghong.ubc.dms.dataTransfer.dao.MoneyMapper;
import ubc.izhonghong.ubc.dms.dataTransfer.pojo.MoneyPo;
import ubc.izhonghong.ubc.dms.dataTransfer.service.MoneyService;

import java.util.Collection;
import java.util.List;

@Service
public class MoneyServiceImpl extends ServiceImpl<MoneyMapper, MoneyPo> implements MoneyService {

    @DS("story")
    public List<MoneyPo> findAllByStory() {
        QueryWrapper<MoneyPo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("money",100);
        return this.baseMapper.selectList(queryWrapper); // 查询全部内容
    }


    @DS("test")
    public List<MoneyPo> findAllByTest(Collection<Long> ids) {
        return this.baseMapper.selectList(null); // 查询全部内容
    }

    @DS("test")
    public boolean insertBatchesByTest(List<MoneyPo> list) {
        return this.saveBatch(list, list.size());
    }
}