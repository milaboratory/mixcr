package com.milaboratory.mixcr.util;

import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.ParameterException;

/**
 * Created by poslavsky on 20/02/2017.
 */
public final class PositiveLongValidator implements IValueValidator<Long> {
    @Override
    public void validate(String name, Long value) throws ParameterException {
        if (value < 0)
            throw new ParameterException(name + ": positive input required (found " + value + ")");
    }
}
