/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses;

import android.app.Dialog;
import android.app.ListActivity;


public class ProtectedListActivity extends ListActivity {
  private Dialog pwDialog;
  @Override
  protected void onPause() {
    super.onPause();
    MyApplication app = MyApplication.getInstance();
    if (app.isLocked)
      pwDialog.dismiss();
    else {
      app.setmLastPause();
    }
  }
  @Override
  protected void onDestroy() {
    super.onDestroy();
    MyApplication.getInstance().setmLastPause();
  }
  @Override
  protected void onResume() {
    super.onResume();
    MyApplication app = MyApplication.getInstance();
    if (app.shouldLock()) {
      if (pwDialog == null)
        pwDialog = DialogUtils.passwordDialog(this);
      DialogUtils.showPasswordDialog(this,pwDialog);
    }
  }
}
