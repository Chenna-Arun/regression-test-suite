package com.testframework.regression.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeoutConfig {

    @Value("${timeouts.ui.pageLoad.seconds:90}")
    private int uiPageLoadSeconds;

    @Value("${timeouts.ui.elementWait.seconds:30}")
    private int uiElementWaitSeconds;

    @Value("${timeouts.ui.perTest.seconds:120}")
    private int uiPerTestSeconds;

    @Value("${timeouts.api.request.seconds:15}")
    private int apiRequestSeconds;

    @Value("${timeouts.api.perTest.seconds:30}")
    private int apiPerTestSeconds;

    @Value("${timeouts.run.global.seconds:0}")
    private int runGlobalSeconds;

    public int getUiPageLoadSeconds() { return uiPageLoadSeconds; }
    public int getUiElementWaitSeconds() { return uiElementWaitSeconds; }
    public int getUiPerTestSeconds() { return uiPerTestSeconds; }
    public int getApiRequestSeconds() { return apiRequestSeconds; }
    public int getApiPerTestSeconds() { return apiPerTestSeconds; }
    public int getRunGlobalSeconds() { return runGlobalSeconds; }
}








