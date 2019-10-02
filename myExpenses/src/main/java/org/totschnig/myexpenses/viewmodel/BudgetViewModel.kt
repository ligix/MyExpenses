package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.model.AggregateAccount
import org.totschnig.myexpenses.model.CurrencyContext
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.DatabaseConstants.*
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.viewmodel.data.Budget
import java.util.*
import javax.inject.Inject

open class BudgetViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {
    val data = MutableLiveData<List<Budget>>()
    val budget = MutableLiveData<Budget>()
    val databaseResult = MutableLiveData<Boolean>()
    val spent = MutableLiveData<Pair<Int, Long>>()
    var spentDisposables = CompositeDisposable()
    @Inject
    lateinit var currencyContext: CurrencyContext
    private val databaseHandler: DatabaseHandler
    val budgetCreatorFunction: (Cursor) -> Budget = { cursor ->
        val currency = cursor.getString(cursor.getColumnIndex(KEY_CURRENCY))
        val currencyUnit = if (currency.equals(AggregateAccount.AGGREGATE_HOME_CURRENCY_CODE))
            Utils.getHomeCurrency() else currencyContext.get(currency)
        Budget(
                cursor.getLong(cursor.getColumnIndex(KEY_ROWID)),
                cursor.getLong(cursor.getColumnIndex(KEY_ACCOUNTID)),
                cursor.getString(cursor.getColumnIndex(KEY_TITLE)),
                cursor.getString(cursor.getColumnIndex(KEY_DESCRIPTION)),
                currencyUnit,
                Money(currencyUnit, cursor.getLong(cursor.getColumnIndex(KEY_BUDGET))),
                Grouping.valueOf(cursor.getString(cursor.getColumnIndex(KEY_GROUPING))),
                cursor.getInt(cursor.getColumnIndex(KEY_COLOR)),
                cursor.getString(cursor.getColumnIndex(KEY_START)),
                cursor.getString(cursor.getColumnIndex(KEY_END))
        )
    }

    init {
        databaseHandler = DatabaseHandler(application.contentResolver)
    }

    init {
        (application as MyApplication).appComponent.inject(this)
    }

    fun loadAllBudgets() {
        disposable = createQuery(null, null)
                .mapToList(budgetCreatorFunction)
                .subscribe { data.postValue(it) }
    }

    fun loadBudget(budgetId: Long, once: Boolean) {
        disposable = createQuery("%s = ?".format(q(KEY_ROWID)), arrayOf(budgetId.toString()))
                .mapToOne(budgetCreatorFunction)
                .subscribe {
                    budget.postValue(it)
                    if (once) dispose()
                }
    }

    fun loadBudgetSpend(position: Int, budget: Budget, aggregate: Boolean) {
        val builder = TransactionProvider.TRANSACTIONS_SUM_URI.buildUpon()
        if (aggregate) {
            builder.appendQueryParameter(TransactionProvider.QUERY_PARAMETER_AGGREGATE_TYPES, "1")
                    .build()
        }
        if (budget.accountId != AggregateAccount.HOME_AGGREGATE_ID.toLong()) {
            if (budget.accountId < 0) {
                builder.appendQueryParameter(KEY_CURRENCY, budget.currency.code())
            } else {
                builder.appendQueryParameter(KEY_ACCOUNTID, budget.accountId.toString())
            }
        }
        spentDisposables.add(briteContentResolver.createQuery(builder.build(),
                null, buildDateFilterClause(budget), null, null, true)
                .mapToOne { cursor -> cursor.getLong(0) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { spent.value = Pair(position, it) })
    }

    protected fun buildDateFilterClause(budget: Budget): String? {
        val year = "$YEAR = $THIS_YEAR"
        return when (budget.grouping) {
            Grouping.YEAR -> year
            Grouping.DAY -> "$year AND $DAY = $THIS_DAY"
            Grouping.WEEK -> getYearOfWeekStart() + " = " + getThisYearOfWeekStart() + " AND " + getWeek() + " = " + getThisWeek()
            Grouping.MONTH -> getYearOfMonthStart() + " = " + getThisYearOfWeekStart() + " AND " + getMonth() + " = " + getThisMonth()
            else -> budget.durationAsSqlFilter()
        }
    }

    fun createQuery(selection: String?, selectionArgs: Array<String>?) =
            briteContentResolver.createQuery(TransactionProvider.BUDGETS_URI,
                    PROJECTION, selection, selectionArgs, null, true)

    fun deleteBudget(budgetId: Long) {
        databaseHandler.startDelete(TOKEN, object: DatabaseHandler.DeleteListener {
            override fun onDeleteComplete(token: Int, result: Int) {
                databaseResult.postValue(result == 1)
            }
        }, TransactionProvider.BUDGETS_URI, KEY_ROWID + " = ?", arrayOf(budgetId.toString()))
    }

    fun updateBudget(budgetId: Long, categoryId: Long, amount: Money) {
        val contentValues = ContentValues(1)
        contentValues.put(KEY_BUDGET, amount.amountMinor)
        val budgetUri = ContentUris.withAppendedId(TransactionProvider.BUDGETS_URI, budgetId)
        databaseHandler.startUpdate(TOKEN, null,
                if (categoryId == 0L) budgetUri else ContentUris.withAppendedId(budgetUri, categoryId),
                contentValues, null, null)
    }

    override fun onCleared() {
        super.onCleared()
        spentDisposables.clear()
    }


    companion object {
        private val TOKEN = 0
        private val PROJECTION = arrayOf(
                q(KEY_ROWID),
                "coalesce(%1\$s, -(select %2\$s from %3\$s where %4\$s = %5\$s), %6\$d) AS %1\$s"
                        .format(Locale.ROOT, KEY_ACCOUNTID, KEY_ROWID, TABLE_CURRENCIES, KEY_CODE,
                                q(KEY_CURRENCY), AggregateAccount.HOME_AGGREGATE_ID),
                KEY_TITLE,
                q(KEY_DESCRIPTION),
                "coalesce(%1\$s.%2\$s, %3\$s.%2\$s) AS %2\$s"
                        .format(TABLE_BUDGETS, KEY_CURRENCY, TABLE_ACCOUNTS),
                KEY_BUDGET,
                q(KEY_GROUPING),
                KEY_COLOR,
                KEY_START,
                KEY_END
        )

        private fun q(column:String) = TABLE_BUDGETS + "." + column
    }
}
