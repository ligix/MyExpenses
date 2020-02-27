package org.totschnig.myexpenses.widget

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.Sort
import org.totschnig.myexpenses.model.Sort.Companion.preferredOrderByForTemplates
import org.totschnig.myexpenses.model.Transfer
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_COLOR
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CURRENCY
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PLANID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SEALED
import org.totschnig.myexpenses.provider.DbUtils
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.CurrencyFormatter
import java.util.*


class TemplateWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TemplatetRemoteViewsFactory(this.applicationContext, intent)
    }
}

class TemplatetRemoteViewsFactory(
        val context: Context,
        intent: Intent
) : AbstractRemoteViewsFactory(context, intent) {
    override fun buildCursor(): Cursor? {
        return context.getContentResolver().query(
                TransactionProvider.TEMPLATES_URI, null, String.format(Locale.ROOT, "%s is null AND %s is null AND %s = 0",
                KEY_PLANID, KEY_PARENTID, KEY_SEALED),
                null, preferredOrderByForTemplates(PrefKey.SORT_ORDER_TEMPLATES, TemplateWidget.getPrefHandler(), Sort.TITLE))
    }


    override fun getViewAt(position: Int) = RemoteViews(context.getPackageName(), R.layout.account_row_widget).apply {
        cursor?.let {
            it.moveToPosition(position)
            setBackgroundColorSave(R.id.divider3, it.getInt(it.getColumnIndex(KEY_COLOR)))
            val title = DbUtils.getString(it, DatabaseConstants.KEY_TITLE)
            val currencyContext = MyApplication.getInstance().getAppComponent().currencyContext()
            val currency = currencyContext.get(DbUtils.getString(it, KEY_CURRENCY))
            val amount = Money(currency, DbUtils.getLongOr0L(it, DatabaseConstants.KEY_AMOUNT))
            val isTransfer = !(it.isNull(it.getColumnIndexOrThrow(DatabaseConstants.KEY_TRANSFER_ACCOUNT)))
            val label = DbUtils.getString(it, DatabaseConstants.KEY_LABEL)
            val comment = DbUtils.getString(it, DatabaseConstants.KEY_COMMENT)
            val payee = DbUtils.getString(it, DatabaseConstants.KEY_PAYEE_NAME)
            setTextViewText(R.id.line1,
                    title + " : " + CurrencyFormatter.instance().formatCurrency(amount))
            val commentSeparator = " / "
            val description = SpannableStringBuilder(if (isTransfer) Transfer.getIndicatorPrefixForLabel(amount.getAmountMinor()) + label else label)
            if (!TextUtils.isEmpty(comment)) {
                if (description.length != 0) {
                    description.append(commentSeparator)
                }
                description.append(comment)
                val before = description.length
                description.setSpan(StyleSpan(Typeface.ITALIC), before, description.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (!TextUtils.isEmpty(payee)) {
                if (description.length != 0) {
                    description.append(commentSeparator)
                }
                description.append(payee)
                val before = description.length
                description.setSpan(UnderlineSpan(), before, description.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setTextViewText(R.id.note, description)
            setOnClickFillInIntent(R.id.object_info, Intent())
            val templateId = it.getLong(it.getColumnIndexOrThrow(KEY_ROWID))
            configureButton(R.id.command1, R.drawable.ic_action_apply_save, CLICK_ACTION_SAVE, R.string.menu_create_instance_save, templateId, 175)
            configureButton(R.id.command2, R.drawable.ic_action_apply_edit, CLICK_ACTION_EDIT, R.string.menu_create_instance_edit, templateId, 223)
            setViewVisibility(R.id.command3, View.GONE)
        }
    }

    protected fun RemoteViews.configureButton(buttonId: Int, drawableResId: Int, action: String, contentDescriptionResId: Int, templateId: Long, minimumWidth: Int) {
        if (width < minimumWidth) {
            setViewVisibility(buttonId, View.GONE)
        } else {
            setViewVisibility(buttonId, View.VISIBLE)
            setImageViewVectorDrawable(buttonId, drawableResId)
            setContentDescription(buttonId, context.getString(contentDescriptionResId))
            setOnClickFillInIntent(buttonId, Intent().apply {
                putExtra(KEY_ROWID, templateId)
                putExtra(AbstractWidget.KEY_CLICK_ACTION, action)
            })
        }
    }
}