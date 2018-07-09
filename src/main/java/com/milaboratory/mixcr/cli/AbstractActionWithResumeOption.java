package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;

/**
 *
 */
abstract class AbstractActionWithResumeOption implements Action {
    @Override
    public void go(ActionHelper helper) throws Exception {
        if (params().skipExecution())
            return;
        go0(helper);
    }

    public abstract void go0(ActionHelper helper) throws Exception;

    @Override
    public abstract ActionParametersWithResumeOption params();
}
