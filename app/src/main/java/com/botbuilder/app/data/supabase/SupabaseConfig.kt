package com.botbuilder.app.data.supabase

/** Your Supabase project's public-facing values. The anon key is DESIGNED to be
 *  embedded in client apps — it only grants what your Row Level Security policies
 *  allow (in this app: a user can only ever read/write their own config row). */
object SupabaseConfig {
    const val PROJECT_URL = "https://nbkwqomitypqnzwafgqq.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5ia3dxb21pdHlwcW56d2FmZ3FxIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODUzMTQ1NjcsImV4cCI6MjEwMDg5MDU2N30.ZhiGPcXNO_lSZopkLQKppkRtdBIKGZwymPjRBIqiNxw"
}
