// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.AuthData
import git4idea.remote.GitHttpAuthDataProvider
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import java.io.IOException

class GithubHttpAuthDataProvider(private val authenticationManager: GithubAuthenticationManager,
                                 private val accountInformationProvider: GithubAccountInformationProvider) : GitHttpAuthDataProvider {
  private val LOG = logger<GithubHttpAuthDataProvider>()

  override fun getAuthData(project: Project, url: String): AuthData? {
    return getSuitableAccounts(project, url, null).singleOrNull()?.let {
      try {
        val username = accountInformationProvider.getAccountUsername(DumbProgressIndicator(), it)
        AuthData(username, authenticationManager.getTokenForAccount(it))
      }
      catch (e: IOException) {
        LOG.info("Cannot load username for $it", e)
        null
      }
    }
  }

  override fun getAuthData(project: Project, url: String, login: String): AuthData? {
    return getSuitableAccounts(project, url, login).singleOrNull()?.let { AuthData(login, authenticationManager.getTokenForAccount(it)) }
  }

  fun getSuitableAccounts(project: Project, url: String, login: String?): Set<GithubAccount> {
    var potentialAccounts = authenticationManager.getAccounts().filter { it.server.matches(url) }

    if (login != null) {
      potentialAccounts = potentialAccounts.filter {
        try {
          accountInformationProvider.getAccountUsername(DumbProgressIndicator(), it) == login
        }
        catch (e: IOException) {
          LOG.info("Cannot load username for $it", e)
          false
        }
      }
    }

    val defaultAccount = authenticationManager.getDefaultAccount(project)
    if (defaultAccount != null && potentialAccounts.contains(defaultAccount)) return setOf(defaultAccount)
    return potentialAccounts.toSet()
  }
}