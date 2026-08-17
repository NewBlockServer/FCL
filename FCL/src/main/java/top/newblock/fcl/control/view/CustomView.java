package top.newblock.fcl.control.view;

import top.newblock.fcl.control.data.CustomControl;

public interface CustomView {
    CustomControl.ViewType getType();
    String getViewId();
    void switchParentVisibility();
    void removeListener();
}
