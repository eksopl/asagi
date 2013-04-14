package net.easymodo.asagi.settings;

import org.apache.commons.beanutils.BeanUtilsBean;

import java.lang.reflect.InvocationTargetException;

public class BeanUtilsNoOverwrite extends BeanUtilsBean {
    @Override
    public void copyProperty(Object dest, String name, Object value)
            throws IllegalAccessException, InvocationTargetException {
        try {
            Object exValue = this.getPropertyUtils().getProperty(dest, name);
            if(exValue == null)
                super.copyProperty(dest, name, value);
        } catch (NoSuchMethodException e) {
            // nothing to do either, just return
        }
    }
}
