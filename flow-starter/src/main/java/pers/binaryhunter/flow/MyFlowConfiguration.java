package pers.binaryhunter.flow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pers.binaryhunter.flow.service.*;
import pers.binaryhunter.flow.service.logic.*;

/**
 * Created by BinaryHunter on 2019/3/14.
 */
@Configuration()
public class MyFlowConfiguration {
    
    @Bean
    public AttachCurrService attachCurrService() {
        return new AttachCurrServiceImpl();
    }

    @Bean
    public AttachHistService attachHistService() {
        return new AttachHistServiceImpl();
    }

    @Bean
    public FlowService flowService() {
        return new FlowServiceImpl();
    }

    @Bean
    public NodeCurrService nodeCurrService() {
        return new NodeCurrServiceImpl();
    }

    @Bean
    public NodeHistService nodeHistService() {
        return new NodeHistServiceImpl();
    }

    @Bean
    public NodeTemplateService nodeTemplateService() {
        return new NodeTemplateServiceImpl();
    }

    @Bean
    public NodeTemplateRefService nodeTemplateRefService() {
        return new NodeTemplateRefServiceImpl();
    }
}
