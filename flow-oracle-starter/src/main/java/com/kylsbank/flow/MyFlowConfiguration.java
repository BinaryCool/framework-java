package com.kylsbank.flow;

import com.kylsbank.flow.service.*;
import com.kylsbank.flow.service.logic.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.kylsbank.flow.service.*;
import com.kylsbank.flow.service.logic.*;

/**
 * Created by BinaryHunter on 2019/3/14.
 */
@Configuration()
public class MyFlowConfiguration {
    
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
