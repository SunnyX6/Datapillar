package com.sunny.datapillar.studio.module.setup.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.studio.module.setup.entity.SystemBootstrap;
import com.sunny.datapillar.studio.module.setup.mapper.SystemBootstrapMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class SetupTokenInitializerTest {

    @Mock
    private SystemBootstrapMapper systemBootstrapMapper;

    private SetupTokenInitializer setupTokenInitializer;

    @BeforeEach
    void setUp() {
        setupTokenInitializer = new SetupTokenInitializer(systemBootstrapMapper);
    }

    @Test
    void run_shouldOnlyLogWhenNotInitialized() throws Exception {
        SystemBootstrap bootstrap = new SystemBootstrap();
        bootstrap.setId(1);
        bootstrap.setSetupCompleted(0);
        when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap);

        setupTokenInitializer.run(new DefaultApplicationArguments(new String[0]));

        verify(systemBootstrapMapper, never()).updateById(any(SystemBootstrap.class));
    }

    @Test
    void run_shouldSkipWhenAlreadyInitialized() throws Exception {
        SystemBootstrap bootstrap = new SystemBootstrap();
        bootstrap.setId(1);
        bootstrap.setSetupCompleted(1);
        when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(bootstrap);

        setupTokenInitializer.run(new DefaultApplicationArguments(new String[0]));

        verify(systemBootstrapMapper, never()).updateById(any(SystemBootstrap.class));
    }

    @Test
    void run_shouldSkipWhenBootstrapMissing() throws Exception {
        when(systemBootstrapMapper.selectByIdForUpdate(1)).thenReturn(null);

        setupTokenInitializer.run(new DefaultApplicationArguments(new String[0]));

        verify(systemBootstrapMapper, never()).updateById(any(SystemBootstrap.class));
    }
}
