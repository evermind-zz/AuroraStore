package com.aurora.store.installer;

import com.aurora.store.util.Root;

interface AppUnInstallerCommon {

    default String ensureCommandSucceeded(Root root,String result) {
        if (result == null || result.length() == 0)
            throw new RuntimeException(root.readError());
        return result;
    }
}
