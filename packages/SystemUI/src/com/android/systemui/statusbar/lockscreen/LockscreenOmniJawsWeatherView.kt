/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

package com.android.systemui.statusbar.lockscreen

import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.internal.util.losp.OmniJawsClient
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.res.R
import com.google.android.systemui.smartspace.DoubleShadowTextView

class LockscreenOmniJawsWeatherView(
    context: Context,
    handler: Handler,
    private val activityStarter: ActivityStarter,
    private val falsingManager: FalsingManager,
) :
    LinearLayout(context),
    OmniJawsClient.OmniJawsObserver {

    private val weatherClient = OmniJawsClient.get()
    private val iconView = ImageView(context)
    private val textView =
        LayoutInflater.from(context)
            .inflate(R.layout.lockscreen_omnijaws_weather_text, this, false) as DoubleShadowTextView
    private var attached = false

    private val settingsObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                updateWeather()
            }
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        val res = resources
        val iconSize = res.getDimensionPixelSize(R.dimen.enhanced_smartspace_icon_size)
        val iconMargin = res.getDimensionPixelSize(R.dimen.enhanced_smartspace_icon_margin)
        val endMargin =
            res.getDimensionPixelSize(R.dimen.enhanced_smartspace_base_action_icon_margin)

        iconView.layoutParams =
            LayoutParams(iconSize, iconSize).apply {
                marginEnd = iconMargin
            }
        addView(iconView)

        addView(textView)
        baselineAlignedChildIndex = 1

        layoutParams =
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = endMargin
            }

        setOnClickListener { openWeatherAfterKeyguardDismiss() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (attached) {
            return
        }
        attached = true
        weatherClient.addObserver(context, this)
        registerSettingsObserver()
        updateWeather()
    }

    override fun onDetachedFromWindow() {
        if (attached) {
            weatherClient.removeObserver(context, this)
            context.contentResolver.unregisterContentObserver(settingsObserver)
            attached = false
        }
        super.onDetachedFromWindow()
    }

    override fun weatherUpdated() {
        updateWeather()
    }

    override fun weatherError(errorReason: Int) {
        if (errorReason == OmniJawsClient.EXTRA_ERROR_DISABLED) {
            visibility = View.GONE
        } else {
            updateWeather()
        }
    }

    override fun updateSettings() {
        updateWeather()
    }

    private fun registerSettingsObserver() {
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.LOCK_SCREEN_WEATHER_ENABLED),
            false,
            settingsObserver,
        )
        resolver.registerContentObserver(OmniJawsClient.WEATHER_URI, true, settingsObserver)
        resolver.registerContentObserver(OmniJawsClient.SETTINGS_URI, true, settingsObserver)
    }

    private fun updateWeather() {
        if (!isLockscreenWeatherEnabled() || !weatherClient.isOmniJawsEnabled(context)) {
            visibility = View.GONE
            return
        }

        weatherClient.queryWeather(context)
        val weatherInfo = weatherClient.weatherInfo
        if (weatherInfo == null || TextUtils.isEmpty(weatherInfo.temp)) {
            visibility = View.GONE
            return
        }

        textView.text = weatherInfo.temp + (weatherInfo.tempUnits ?: "")
        setWeatherIcon(weatherClient.getWeatherConditionImage(context, weatherInfo.conditionCode))
        visibility = View.VISIBLE
    }

    private fun setWeatherIcon(icon: Drawable?) {
        if (icon == null) {
            iconView.visibility = View.GONE
            return
        }
        iconView.setImageDrawable(icon)
        iconView.visibility = View.VISIBLE
    }

    private fun openWeatherAfterKeyguardDismiss() {
        if (falsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
            return
        }
        context.packageManager.getLaunchIntentForPackage(OmniJawsClient.SERVICE_PACKAGE)?.let {
            activityStarter.postStartActivityDismissingKeyguard(it, 0)
        }
    }

    private fun isLockscreenWeatherEnabled(): Boolean {
        return Settings.Secure.getIntForUser(
            context.contentResolver,
            Settings.Secure.LOCK_SCREEN_WEATHER_ENABLED,
            1,
            context.userId,
        ) == 1
    }
}
