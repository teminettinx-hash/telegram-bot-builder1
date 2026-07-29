package com.botbuilder.app

import android.app.Application
import com.botbuilder.app.billing.BillingManager
import com.botbuilder.app.data.local.SecureStore

class BotBuilderApp : Application() {

    lateinit var billingManager: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        val secureStore = SecureStore(applicationContext)
        billingManager = BillingManager(applicationContext, secureStore) { /* tier cached in SecureStore; screens re-read it as needed */ }
        billingManager.startConnection()
    }
}
