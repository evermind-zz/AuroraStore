/*
 * Aurora Store
 * Copyright (C) 2019, Rahul Kumar Patel <whyorean@gmail.com>
 *
 * Split APK Installer (SAI)
 * Copyright (C) 2018, Aefyr
 *
 * Aurora Store is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Aurora Store is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package com.aurora.store.installer;

import com.aurora.store.model.App;
import com.aurora.store.util.Log;
import com.aurora.store.util.Root;

public class AppUninstallerRooted implements AppUnInstallerCommon{

    private Root root;

    public AppUninstallerRooted() {
        root = new Root();
    }

    protected void uninstall(App app) {
        try {
            if (root.isTerminated() || !root.isAcquired()) {
                root = new Root();
                if (!root.isAcquired()) {
                    return;
                }
            }
            // redirect stderr to stdout as at least on KitKat Emulator the result 'Success'
            // is not present on stdout
            Log.d(ensureCommandSucceeded(root,root.exec("pm clear " + app.getPackageName() + " 2>&1")));
            Log.d(ensureCommandSucceeded(root,root.exec("pm uninstall " + app.getPackageName())));
        } catch (Exception e) {
            Log.w(e.getMessage());
        }
    }
}
