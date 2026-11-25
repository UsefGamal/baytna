package com.baytna.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.OutlinedButton
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.baytna.app.ui.CloudViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.baytna.app.R
import com.baytna.app.data.repo.AccountSetup
import com.baytna.app.data.repo.HouseholdSetup
import com.baytna.app.ui.MainViewModel
import com.baytna.app.ui.currentLocale
import com.baytna.domain.model.AccountType
import com.baytna.domain.money.MoneyParser

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var showCloud by rememberSaveable { mutableStateOf(false) }
    if (showCloud) {
        val cloudVm: CloudViewModel = viewModel(factory = viewModelFactory { initializer { CloudViewModel(vm.container) } })
        BackHandler { showCloud = false }
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            CloudScreen(cloudVm, household = null, members = emptyList(), meId = null, pending = 0) { showCloud = false }
        }
        return
    }
    var householdName by rememberSaveable { mutableStateOf("") }
    var myName by rememberSaveable { mutableStateOf("") }
    var partnerName by rememberSaveable { mutableStateOf("") }
    var cashOpening by rememberSaveable { mutableStateOf("") }
    var bankOpening by rememberSaveable { mutableStateOf("") }
    var saving by rememberSaveable { mutableStateOf(false) }
    val defaultHouseholdName = stringResource(R.string.onboarding_default_household)
    val cashName = stringResource(R.string.default_account_cash)
    val bankName = stringResource(R.string.default_account_bank)
    val language = currentLocale().language

    val cashMinor = if (cashOpening.isBlank()) 0L else MoneyParser.parse(cashOpening)?.minor
    val bankMinor = if (bankOpening.isBlank()) 0L else MoneyParser.parse(bankOpening)?.minor
    val valid = myName.isNotBlank() && cashMinor != null && bankMinor != null

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.onboarding_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showCloud = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_join))
            }

            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language != "ar", onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }, label = { Text("English") })
                FilterChip(selected = language == "ar", onClick = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
                }, label = { Text("العربية") })
            }

            OutlinedTextField(householdName, { householdName = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.household_name)) }, placeholder = { Text(defaultHouseholdName) }, singleLine = true)
            OutlinedTextField(myName, { myName = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.your_name)) }, singleLine = true)
            OutlinedTextField(partnerName, { partnerName = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.partner_name)) }, singleLine = true,
                supportingText = { Text(stringResource(R.string.partner_name_hint)) })

            Text(stringResource(R.string.starting_balances), style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(cashOpening, { cashOpening = it }, Modifier.fillMaxWidth(),
                label = { Text(cashName) }, singleLine = true, isError = cashMinor == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(stringResource(R.string.currency_symbol)) })
            OutlinedTextField(bankOpening, { bankOpening = it }, Modifier.fillMaxWidth(),
                label = { Text(bankName) }, singleLine = true, isError = bankMinor == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(stringResource(R.string.currency_symbol)) })
            Text(stringResource(R.string.onboarding_currency_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    saving = true
                    val setup = HouseholdSetup(
                        householdName = householdName.ifBlank { defaultHouseholdName },
                        myName = myName,
                        partnerName = partnerName,
                        accounts = listOf(
                            AccountSetup(cashName, AccountType.CASH, cashMinor ?: 0L),
                            AccountSetup(bankName, AccountType.BANK, bankMinor ?: 0L),
                        ),
                        localeTag = language,
                    )
                    vm.launch(onSuccess = { saving = false }, onFailure = { saving = false }) {
                        val me = createHousehold(setup)
                        vm.setCurrentMember(me)
                    }
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.create_household)) }
        }
    }
}
