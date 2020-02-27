package org.totschnig.myexpenses.widget

import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.ManageTemplates
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.provider.DatabaseConstants

const val CLICK_ACTION_SAVE = "save"
const val CLICK_ACTION_EDIT = "edit"

class TemplateWidget2: AbstractWidget2(TemplateWidgetService::class.java) {
    override fun handleWidgetClick(context: Context, intent: Intent) {
        val templateId = intent.getLongExtra(DatabaseConstants.KEY_ROWID, 0)
        val clickAction = intent.getStringExtra(AbstractWidget.KEY_CLICK_ACTION)
        when (clickAction) {
            null -> {
                context.startActivity(Intent(context, ManageTemplates::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AbstractWidget.EXTRA_START_FROM_WIDGET, true)
                })
            }
            CLICK_ACTION_SAVE -> {
                if (MyApplication.getInstance().shouldLock(null)) {
                    Toast.makeText(context,
                            context.getString(R.string.warning_instantiate_template_from_widget_password_protected),
                            Toast.LENGTH_LONG).show()
                } else {
                    val t = Transaction.getInstanceFromTemplate(templateId)
                    if (t != null && t.save() != null) {
                        Toast.makeText(context,
                                context.resources.getQuantityString(R.plurals.save_transaction_from_template_success, 1, 1),
                                Toast.LENGTH_LONG).show()
                    }
                }
            }
            CLICK_ACTION_EDIT -> {
                TODO()
            }
        }
    }
}