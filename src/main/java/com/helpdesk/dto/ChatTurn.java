package com.helpdesk.dto;

import java.io.Serializable;

/** One message in a chatbot conversation. role is "user" or "assistant". */
public record ChatTurn(String role, String content) implements Serializable {
}
