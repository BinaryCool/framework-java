package pers.binaryhunter.rabbitmq.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RabbitMQQueue {
    private String name;
    private Boolean delayed;
    private String env;
}
