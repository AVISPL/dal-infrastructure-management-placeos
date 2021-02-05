package com.vizcom.symphony.dal.communicator.vizcom.common;

public interface ISymphonyConfig {
    void setHost(String host);
    void setPort(int port);
    void setProtocol(String protocol);
    void setLogin(String username);
    void setPassword(String password);
}