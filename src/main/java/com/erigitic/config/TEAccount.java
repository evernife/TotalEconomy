/*
 * This file is part of Total Economy, licensed under the MIT License (MIT).
 *
 * Copyright (c) Eric Grandt <https://www.ericgrandt.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.erigitic.config;

import com.erigitic.jobs.Job;
import com.erigitic.main.TotalEconomy;
import com.erigitic.sql.SqlManager;
import com.erigitic.sql.SqlQuery;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.erigitic.util.StringUtil;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.economy.account.Account;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.service.economy.transaction.TransactionType;
import org.spongepowered.api.service.economy.transaction.TransactionTypes;
import org.spongepowered.api.service.economy.transaction.TransferResult;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

public class TEAccount implements UniqueAccount {

    private final TotalEconomy totalEconomy;
    private final Logger logger;
    private final AccountManager accountManager;
    private final UUID uuid;
    private final SqlManager sqlManager;

    private final boolean databaseEnabled;

    /**
     * Constructor for the TEAccount class. Manages a unique account, identified by a {@link UUID}, that contains balances for each {@link Currency}.
     *
     * @param uuid The UUID of the account
     */
    public TEAccount(UUID uuid) {
        this.uuid = uuid;

        totalEconomy = TotalEconomy.getInstance();
        logger = totalEconomy.getLogger();
        accountManager = totalEconomy.getAccountManager();
        databaseEnabled = totalEconomy.isDatabaseEnabled();
        sqlManager = (databaseEnabled ? totalEconomy.getSqlManager() : null);
    }

    /**
     * Gets the display name associated with the account.
     *
     * @return Text The display name
     */
    @Override
    public Text getDisplayName() {
        if (totalEconomy.getUserStorageService().get(uuid).isPresent()) {
            return Text.of(totalEconomy.getUserStorageService().get(uuid).get().getName());
        }

        return Text.of("PLAYER NAME");
    }

    @Override
    public BigDecimal getDefaultBalance(Currency currency) {
        return ((TECurrency) currency).getStartingBalance();
    }

    /**
     * Determines if a balance exists for a {@link Currency}.
     *
     * @param currency Currency type to be checked for
     * @param contexts The contexts that the check occurred in
     * @return boolean If a balance exists for the specified currency
     */
    @Override
    public boolean hasBalance(Currency currency, Set<Context> contexts) {
        String currencyName = currency.getDisplayName().toPlain().toLowerCase();

        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                    .select(currencyName + "_balance")
                    .from("accounts")
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            return sqlQuery.recordExists();
        } else {
            return accountManager.getAccountConfig().getNode(uuid.toString(), currencyName + "-balance").getValue() != null;
        }
    }

    /**
     * Gets the balance of a {@link Currency}.
     *
     * @param currency The currency to get the balance of
     * @param contexts The contexts that the check occurred in
     * @return BigDecimal The balance
     */
    @Override
    public BigDecimal getBalance(Currency currency, Set<Context> contexts) {
        if (hasBalance(currency, contexts)) {
            String currencyName = currency.getDisplayName().toPlain().toLowerCase();

            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                        .select(currencyName + "_balance")
                        .from("accounts")
                        .where("uid")
                        .equals(uuid.toString())
                        .build();

                return sqlQuery.getBigDecimal(BigDecimal.ZERO);
            } else {
                BigDecimal balance = new BigDecimal(accountManager.getAccountConfig().getNode(uuid.toString(), currencyName + "-balance").getString());

                return balance;
            }
        }

        return BigDecimal.ZERO;
    }

    /**
     * Get a player's balance for each currency type.
     *
     * @param contexts The contexts that the check occurred in
     * @return Map A map of the balances of each currency
     */
    @Override
    public Map<Currency, BigDecimal> getBalances(Set<Context> contexts) {
        HashMap<Currency, BigDecimal> balances = new HashMap<>();

        for (Currency currency : totalEconomy.getCurrencies()) {
            balances.put(currency, getBalance(currency, contexts));
        }

        return balances;
    }

    /**
     * Sets the balance of a {@link Currency}.
     *
     * @param currency Currency to set the balance of
     * @param amount Amount to set the balance to
     * @param cause The cause of the transaction
     * @param contexts The contexts that the check occurred in
     * @return TransactionResult Result of the transaction
     */
    @Override
    public TransactionResult setBalance(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        TransactionResult transactionResult;
        String currencyName = currency.getDisplayName().toPlain().toLowerCase();

        // If the amount is greater then the money cap, set the amount to the money cap
        amount = amount.min(totalEconomy.getMoneyCap());

        if (hasBalance(currency, contexts)) {
            BigDecimal delta = amount.subtract(getBalance(currency));
            TransactionType transactionType = delta.compareTo(BigDecimal.ZERO) >= 0 ? TransactionTypes.DEPOSIT : TransactionTypes.WITHDRAW;

            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                        .update("accounts")
                        .set(currencyName + "_balance")
                        .equals(amount.setScale(2, BigDecimal.ROUND_DOWN).toPlainString())
                        .where("uid")
                        .equals(uuid.toString())
                        .build();

                if (sqlQuery.getRowsAffected() > 0) {
                    transactionResult = new TETransactionResult(this, currency, delta.abs(), contexts, ResultType.SUCCESS, transactionType);
                } else {
                    transactionResult = new TETransactionResult(this, currency, delta.abs(), contexts, ResultType.FAILED, transactionType);
                }
            } else {
                accountManager.getAccountConfig().getNode(uuid.toString(), currencyName + "-balance").setValue(amount.setScale(2, BigDecimal.ROUND_DOWN));
                accountManager.requestConfigurationSave();

                transactionResult = new TETransactionResult(this, currency, delta.abs(), contexts, ResultType.SUCCESS, transactionType);
            }
        } else {
            transactionResult = new TETransactionResult(this, currency, BigDecimal.ZERO, contexts, ResultType.FAILED, TransactionTypes.DEPOSIT);
        }

        totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transactionResult));

        return transactionResult;
    }

    /**
     * Resets all currency balances to their starting balances.
     *
     * @param cause The cause of the transaction
     * @param contexts The contexts that the check occurred in
     * @return Map Map of transaction results
     */
    @Override
    public Map<Currency, TransactionResult> resetBalances(Cause cause, Set<Context> contexts) {
        Map<Currency, TransactionResult> result = new HashMap<>();

        for (Currency currency : totalEconomy.getCurrencies()) {
            result.put(currency, resetBalance(currency, cause, contexts));
        }

        return result;
    }

    /**
     * Reset a currencies balance to its starting balance.
     *
     * @param currency The balance to reset
     * @param cause The cause of the transaction
     * @param contexts The contexts that the check occurred in
     * @return TransactionResult Result of the reset
     */
    @Override
    public TransactionResult resetBalance(Currency currency, Cause cause, Set<Context> contexts) {
        return setBalance(currency, ((TECurrency) currency).getStartingBalance(), cause);
    }

    /**
     * Add money to a balance.
     *
     * @param currency The balance to deposit money into
     * @param amount Amount to deposit
     * @param cause The cause of the transaction
     * @param contexts The contexts that the check occurred in
     * @return TransactionResult Result of the deposit
     */
    @Override
    public TransactionResult deposit(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        BigDecimal curBalance = getBalance(currency, contexts);
        BigDecimal newBalance = curBalance.add(amount);

        return setBalance(currency, newBalance, cause);
    }

    /**
     * Remove money from a balance.
     *
     * @param currency The balance to withdraw money from
     * @param amount Amount to withdraw
     * @param cause The cause of the transaction
     * @param contexts The contexts that the check occurred in
     * @return TransactionResult Result of the withdrawal
     */
    @Override
    public TransactionResult withdraw(Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        BigDecimal curBalance =  getBalance(currency, contexts);
        BigDecimal newBalance = curBalance.subtract(amount);

        if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
            return setBalance(currency, newBalance, cause);
        }

        return new TETransactionResult(this, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.WITHDRAW);
    }

    /**
     * Transfer money between two TEAccount's.
     *
     * @param to Account to transfer money to
     * @param currency Type of currency to transfer
     * @param amount Amount to transfer
     * @param cause The cause of the transaction
     * @param contexts The contexts that the check occurred in
     * @return TransactionResult Result of the reset
     */
    @Override
    public TransferResult transfer(Account to, Currency currency, BigDecimal amount, Cause cause, Set<Context> contexts) {
        TransferResult transferResult;

        if (hasBalance(currency, contexts)) {
            BigDecimal curBalance = getBalance(currency, contexts);
            BigDecimal newBalance = curBalance.subtract(amount);

            if (newBalance.compareTo(BigDecimal.ZERO) >= 0) {
                withdraw(currency, amount, cause, contexts);

                if (to.hasBalance(currency)) {
                    to.deposit(currency, amount, cause, contexts);

                    transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.SUCCESS, TransactionTypes.TRANSFER);
                    totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

                    return transferResult;
                } else {
                    transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.FAILED, TransactionTypes.TRANSFER);
                    totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

                    return transferResult;
                }
            } else {
                transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.ACCOUNT_NO_FUNDS, TransactionTypes.TRANSFER);
                totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

                return transferResult;
            }
        }

        transferResult = new TETransferResult(this, to, currency, amount, contexts, ResultType.FAILED, TransactionTypes.TRANSFER);
        totalEconomy.getGame().getEventManager().post(new TEEconomyTransactionEvent(transferResult));

        return transferResult;
    }

    /**
     * Get the account identifier.
     *
     * @return String The identifier
     */
    @Override
    public String getIdentifier() {
        return uuid.toString();
    }

    /**
     * Get the {@link UUID} of the account.
     *
     * @return UUID The UUID of the account
     */
    @Override
    public UUID getUniqueId() {
        return uuid;
    }

    @Override
    public Set<Context> getActiveContexts() {
        return new HashSet<>();
    }

    public boolean setCurrentJob(String jobName) {
        jobName = jobName.toLowerCase();

        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                    .update("accounts")
                    .set("job")
                    .equals(jobName)
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            if (sqlQuery.getRowsAffected() <= 0) {
                logger.warn("An error occurred while changing the job of " + uuid + "/" + getDisplayName() + "!");
                return false;
            }

            return true;
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            accountConfig.getNode(uuid.toString(), "job").setValue(jobName);

            accountConfig.getNode(uuid.toString(), "jobstats", jobName, "level").setValue(
                    accountConfig.getNode(uuid.toString(), "jobstats", jobName, "level").getInt(1));

            accountConfig.getNode(uuid.toString(), "jobstats", jobName, "exp").setValue(
                    accountConfig.getNode(uuid.toString(), "jobstats", jobName, "exp").getInt(0));

            try {
                accountManager.getConfigManager().save(accountConfig);
            } catch (IOException e) {
                logger.warn("An error occurred while changing the job of " + uuid + "/" + getDisplayName() + "!");
                return false;
            }

            return true;
        }
    }

    public String getCurrentJobName() {
        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                    .select("job")
                    .from("accounts")
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            return sqlQuery.getString("unemployed").toLowerCase();
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            return accountConfig.getNode(uuid.toString(), "job").getString("unemployed").toLowerCase();
        }
    }

    public boolean hasJobNotifications() {
        if (databaseEnabled) {
            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource()).select("job_notifications")
                    .from("accounts")
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            return sqlQuery.getBoolean(true);
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            return accountConfig.getNode(uuid.toString(), "jobnotifications").getBoolean(true);
        }
    }

    public int getCurrentJobLevel() {
        String currentJob = getCurrentJobName();

        if (!currentJob.equals("unemployed")) {
            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                        .select(currentJob)
                        .from("levels")
                        .where("uid")
                        .equals(uuid.toString())
                        .build();

                return sqlQuery.getInt(1);
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                return accountConfig.getNode(uuid.toString(), "jobstats", currentJob, "level").getInt(1);
            }
        }

        return 1;
    }

    public int getJobLevel(String jobName) {
        jobName = jobName.toLowerCase();

        if (!jobName.equals("unemployed")) {
            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                        .select(jobName)
                        .from("levels")
                        .where("uid")
                        .equals(uuid.toString())
                        .build();

                return sqlQuery.getInt(1);
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                return accountConfig.getNode(uuid.toString(), "jobstats", jobName, "level").getInt(1);
            }
        }

        return 1;
    }

    public int getCurrentJobExp() {
        String currentJob = getCurrentJobName();

        if (!currentJob.equals("unemployed")) {
            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                        .select(currentJob)
                        .from("experience")
                        .where("uid")
                        .equals(uuid.toString())
                        .build();

                return sqlQuery.getInt(0);
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                return accountConfig.getNode(uuid.toString(), "jobstats", currentJob, "exp").getInt(0);
            }
        }

        return 0;
    }

    public int getJobExp(String jobName) {
        jobName = jobName.toLowerCase();

        if (!jobName.equals("unemployed")) {
            if (databaseEnabled) {
                SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                        .select(jobName)
                        .from("experience")
                        .where("uid")
                        .equals(uuid.toString())
                        .build();

                return sqlQuery.getInt(0);
            } else {
                ConfigurationNode accountConfig = accountManager.getAccountConfig();

                return accountConfig.getNode(uuid.toString(), "jobstats", jobName, "exp").getInt(0);
            }
        }

        return 0;
    }

    public void setCurrentJobLevel(int level) {
        String currentJobName = getCurrentJobName();

        if (databaseEnabled) {
            SqlQuery.builder(sqlManager.getDataSource())
                    .update("levels")
                    .set(currentJobName)
                    .equals(String.valueOf(level))
                    .where("uid")
                    .equals(uuid.toString())
                    .build();
        } else {
            ConfigurationNode accountConfig = accountManager.getAccountConfig();

            accountConfig.getNode(uuid.toString(), "jobstats", currentJobName, "level").setValue(level);
        }
    }

    public boolean addExpToCurrentJob(int exp) {
        String currentJobName = getCurrentJobName();

        if (databaseEnabled) {
            int newExp = getCurrentJobExp() + exp;

            SqlQuery sqlQuery = SqlQuery.builder(sqlManager.getDataSource())
                    .update("experience")
                    .set(currentJobName)
                    .equals(String.valueOf(newExp))
                    .where("uid")
                    .equals(uuid.toString())
                    .build();

            if (sqlQuery.getRowsAffected() <= 0) {
                logger.warn("An error occurred while updating job experience in the database!");
                return false;
            }
        } else {
            int curExp = getCurrentJobExp();

            ConfigurationNode accountConfig = accountManager.getAccountConfig();
            accountConfig.getNode(uuid.toString(), "jobstats", currentJobName, "exp").setValue(curExp + exp);

            accountManager.requestConfigurationSave();
        }

        return true;
    }
}
