package com.infernokun.amaterasu.websocket;

import com.infernokun.amaterasu.models.entities.Notification;

/** Published when a notification is stored, so the recipient's open sockets get it at once. */
public record NotificationCreatedEvent(Notification notification) {
}
