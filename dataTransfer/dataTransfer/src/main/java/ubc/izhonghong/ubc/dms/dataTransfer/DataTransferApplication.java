package ubc.izhonghong.ubc.dms.dataTransfer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import ubc.izhonghong.ubc.dms.dataTransfer.pojo.MoneyPo;
import ubc.izhonghong.ubc.dms.dataTransfer.service.impl.StoryMoneyServiceImpl;
import ubc.izhonghong.ubc.dms.dataTransfer.service.impl.TestMoneyServiceImpl;

import java.util.List;


@SpringBootApplication
@EnableScheduling
@MapperScan("ubc.izhonghong.ubc.dms.dataTransfer.dao")
public class DataTransferApplication {

    public DataTransferApplication(TestMoneyServiceImpl testMoneyService, StoryMoneyServiceImpl storyMoneyService) {
        //List<MoneyPo> moneyPoList = testMoneyService.listByIds(Arrays.asList(1, 1000));
        List<MoneyPo> moneyPoList = testMoneyService.list();
        System.out.println(moneyPoList);
        System.out.println("--------------");

        //List<MoneyPo> moneyPoList2 = storyMoneyService.listByIds(Arrays.asList(1, 1000));
        List<MoneyPo> moneyPoList2 =  storyMoneyService.list();

        System.out.println(moneyPoList2);
    }

    public static void main(String[] args) {
        SpringApplication.run(DataTransferApplication.class, args);
    }

}
