package pers.binaryhunter.rabbitmq.util;

import com.alibaba.fastjson.JSON;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pers.binaryhunter.framework.utils.UUIDUtil;

public class RabbitMQUtil {
    public static void convertAndSend(RabbitTemplate rabbitTemplate, String env, String queueName, Object params) {
        convertAndSend(rabbitTemplate, env, queueName, params, null);
    }

    public static void convertAndSend(RabbitTemplate rabbitTemplate, String env, String queueName, Object params, Integer delayInMillis) {
        String json;
        if (params instanceof String) {
            json = params.toString();
        } else {
            json = JSON.toJSONString(params);
        }
        if (null == delayInMillis || 0 >= delayInMillis) {
            rabbitTemplate.convertAndSend(queueName + ".Exchange-" + env, queueName + ".message", json,
                    new CorrelationData(UUIDUtil.get32UUID()));
        } else {
            rabbitTemplate.convertAndSend(queueName + ".Exchange-" + env, queueName + ".message", json,
                    message -> {
                        //设置消息持久化
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setDelay(delayInMillis);
                        return message;
                    },
                    new CorrelationData(UUIDUtil.get32UUID()));
        }
    }
}
