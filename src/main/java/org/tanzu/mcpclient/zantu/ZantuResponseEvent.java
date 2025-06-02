package org.tanzu.mcpclient.zantu;

import org.springframework.context.ApplicationEvent;

public class ZantuResponseEvent extends ApplicationEvent {
    private final ZantuChatMessage message;

    public ZantuResponseEvent(Object source, ZantuChatMessage message) {
        super(source);
        this.message = message;
    }

    public ZantuChatMessage getMessage() {
        return message;
    }
}