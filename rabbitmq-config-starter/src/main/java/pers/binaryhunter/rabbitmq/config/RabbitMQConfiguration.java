package pers.binaryhunter.rabbitmq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;
import pers.binaryhunter.framework.utils.NullUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

@Configuration
@ConditionalOnClass({ RabbitTemplate.class })
@EnableConfigurationProperties(RabbitMQProperties.class)
@Import(RabbitMQConfiguration.ImportConfig.class)
@Slf4j
public class RabbitMQConfiguration {

    @Component
    public static class ImportConfig implements ImportBeanDefinitionRegistrar, EnvironmentAware, ApplicationContextAware {
        private RabbitMQProperties rabbitMQProperties;
        private String env;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
            var queueList = rabbitMQProperties.getQueues();
            if (NullUtil.isEmpty(queueList)) {
                log.warn("Queue list is empty!");
                return;
            }

            for (var queue : queueList) {
                if (NullUtil.isBlank(queue.getName())) {
                    log.warn("Queue name is blank!");
                    continue;
                }
                // 注册bean
                this.registerExchange(beanDefinitionRegistry, queue);
                this.registerQueue(beanDefinitionRegistry, queue);
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            // 通过Binder将environment中的值转成对象
            rabbitMQProperties = Binder.get(environment).bind(getPropertiesPrefix(RabbitMQProperties.class), RabbitMQProperties.class).get();
            env = environment.getActiveProfiles()[0];
        }

        private String getPropertiesPrefix(Class<?> tClass) {
            var str = Objects.requireNonNull(AnnotationUtils.getAnnotation(tClass, ConfigurationProperties.class)).prefix();
            log.info("Get properties prefix {}", str);
            return str;
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            log.info("Rabbit bind set application context");
            BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) ((ConfigurableApplicationContext)applicationContext).getBeanFactory();
            var queueList = rabbitMQProperties.getQueues();
            if (NullUtil.isEmpty(queueList)) {
                log.warn("Queue list is empty!");
                return;
            }

            for (var queue : queueList) {
                this.registerBind(applicationContext, beanFactory, queue);
            }
        }

        private void registerExchange(BeanDefinitionRegistry beanDefinitionRegistry, RabbitMQQueue queue) {
            String envSuffix = queue.getEnv();
            if (NullUtil.isBlank(envSuffix)) {
                envSuffix = env;
            }
            String name = queue.getName() + "." + "Exchange" + "-" + envSuffix;

            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(TopicExchange.class);
            beanDefinitionBuilder.addConstructorArgValue(name);
            beanDefinitionBuilder.addConstructorArgValue(true);
            beanDefinitionBuilder.addConstructorArgValue(false);
            if (null != queue.getDelayed() && queue.getDelayed()) {
                beanDefinitionBuilder.addPropertyValue("delayed", true);
            }

            BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
            beanDefinitionRegistry.registerBeanDefinition(queue.getName() + "Exchange", beanDefinition);
            log.info("RegisterBean {}.", name);
        }

        private void registerQueue(BeanDefinitionRegistry beanDefinitionRegistry, RabbitMQQueue queue) {
            String delayStr = null != queue.getDelayed() && queue.getDelayed() ? "Delay" : "";
            String envSuffix = queue.getEnv();
            if (NullUtil.isBlank(envSuffix)) {
                envSuffix = env;
            }
            String name = queue.getName() + "." + delayStr + "Queue" + "-" + envSuffix;

            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(Queue.class);
            beanDefinitionBuilder.addConstructorArgValue(name);
            beanDefinitionBuilder.addConstructorArgValue(true);

            BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
            beanDefinitionRegistry.registerBeanDefinition(queue.getName() + "Queue", beanDefinition);
            log.info("RegisterBean {}.", name);
        }

        private void registerBind(ApplicationContext applicationContext, BeanDefinitionRegistry beanDefinitionRegistry, RabbitMQQueue queue) {
            // 注册bean
            var exchangeBean = applicationContext.getBean(queue.getName() + "Exchange", TopicExchange.class);
            var queueBean = applicationContext.getBean(queue.getName() + "Queue", Queue.class);

            String destination = queueBean.getName();
            Binding.DestinationType destinationType = Binding.DestinationType.QUEUE;
            String exchange = exchangeBean.getName();
            String routingKey = queue.getName() + ".#";
            Map<String, Object> arguments = Collections.emptyMap();

            String envSuffix = queue.getEnv();
            if (NullUtil.isBlank(envSuffix)) {
                envSuffix = env;
            }
            String delayStr = null != queue.getDelayed() && queue.getDelayed() ? "Delay" : "";
            String name = queue.getName() + "." + delayStr + "Binding" + "-" + envSuffix;

            BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(Binding.class);
            beanDefinitionBuilder.addConstructorArgValue(destination);
            beanDefinitionBuilder.addConstructorArgValue(destinationType);
            beanDefinitionBuilder.addConstructorArgValue(exchange);
            beanDefinitionBuilder.addConstructorArgValue(routingKey);
            beanDefinitionBuilder.addConstructorArgValue(arguments);

            BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
            beanDefinitionRegistry.registerBeanDefinition(queue.getName() + "Binding", beanDefinition);
            log.info("RegisterBean {}.", name);
        }
    }
}