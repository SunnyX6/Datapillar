package com.sunny.job.admin.dto;

import java.util.Date;
import java.util.List;

/**
 * 执行器运维管理DTO
 * 用于运维人员查看和管理executor的在线/离线状态
 */
public class ExecutorManagementDTO {

    private int groupId;                    // 执行器组ID
    private String appname;                 // 应用名称
    private String title;                   // 执行器名称
    private int addressType;                // 执行器地址类型：0=自动注册、1=手动录入
    private List<String> onlineAddressList; // 在线的执行器地址列表
    private int onlineCount;                // 在线数量
    private Date updateTime;                // 最后更新时间

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getAddressType() {
        return addressType;
    }

    public void setAddressType(int addressType) {
        this.addressType = addressType;
    }

    public List<String> getOnlineAddressList() {
        return onlineAddressList;
    }

    public void setOnlineAddressList(List<String> onlineAddressList) {
        this.onlineAddressList = onlineAddressList;
    }

    public int getOnlineCount() {
        return onlineCount;
    }

    public void setOnlineCount(int onlineCount) {
        this.onlineCount = onlineCount;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
