package com.xlabsx27.naveeoffline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xlabsx27.naveeoffline.R
import com.xlabsx27.naveeoffline.ui.components.BackButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.about)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("${stringResource(R.string.app_name)} $version", style = MaterialTheme.typography.headlineSmall)
            Para(stringResource(R.string.about_intro))
            Head(stringResource(R.string.about_licence))
            Para(stringResource(R.string.about_licence_text))
            Head(stringResource(R.string.about_credits))
            Para(stringResource(R.string.about_credits_maps))
            Para(stringResource(R.string.about_credits_protocol))
            Head(stringResource(R.string.about_disclaimer))
            Para(stringResource(R.string.about_disclaimer_text))
            Para(stringResource(R.string.about_warranty))
        }
    }
}

@Composable
private fun Head(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun Para(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)
