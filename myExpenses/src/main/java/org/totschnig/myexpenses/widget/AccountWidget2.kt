package org.totschnig.myexpenses.widget

import android.content.Context
import android.content.Intent
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.activity.MyExpenses
import org.totschnig.myexpenses.contract.TransactionsContract
import org.totschnig.myexpenses.provider.DatabaseConstants

const val CLICK_ACTION_NEW_TRANSACTION = "newTransaction"
const val CLICK_ACTION_NEW_TRANSFER = "newTransfer"
const val CLICK_ACTION_NEW_SPLIT = "newSplit"


class AccountWidget2 : AbstractWidget2(AccountWidgetService::class.java) {
    override fun handleWidgetClick(context: Context, intent: Intent) {
        val accountId = intent.getLongExtra(DatabaseConstants.KEY_ROWID, 0)
        val clickAction = intent.getStringExtra(AbstractWidget.KEY_CLICK_ACTION)
        when (clickAction) {
            null -> {
                context.startActivity(Intent(context, MyExpenses::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(DatabaseConstants.KEY_ROWID, accountId)
                })
            }
            else -> context.startActivity(Intent(context, ExpenseEdit::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (accountId < 0) {
                    putExtra(DatabaseConstants.KEY_CURRENCY, intent.getStringExtra(DatabaseConstants.KEY_CURRENCY))
                } else {
                    putExtra(DatabaseConstants.KEY_ACCOUNTID, accountId)
                }
                putExtra(AbstractWidget.EXTRA_START_FROM_WIDGET, true)
                putExtra(AbstractWidget.EXTRA_START_FROM_WIDGET_DATA_ENTRY, true)
                putExtra(TransactionsContract.Transactions.OPERATION_TYPE, when(clickAction) {
                    CLICK_ACTION_NEW_TRANSACTION ->  TransactionsContract.Transactions.TYPE_TRANSACTION
                    CLICK_ACTION_NEW_TRANSFER -> TransactionsContract.Transactions.TYPE_TRANSFER
                    CLICK_ACTION_NEW_SPLIT -> TransactionsContract.Transactions.TYPE_SPLIT
                    else -> throw IllegalArgumentException()
                })
            })
        }
    }
}