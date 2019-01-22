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

package com.erigitic.commands;

import com.erigitic.config.AccountManager;
import com.erigitic.config.TEAccount;
import com.erigitic.main.TotalEconomy;
import com.erigitic.util.MessageManager;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;

import java.util.Optional;

public class BalanceCommand implements CommandExecutor {
    private TotalEconomy totalEconomy;
    private AccountManager accountManager;
    private MessageManager messageManager;
    private Currency defaultCurrency;

    public BalanceCommand(TotalEconomy totalEconomy, AccountManager accountManager, MessageManager messageManager) {
        this.totalEconomy = totalEconomy;
        this.accountManager = accountManager;
        this.messageManager = messageManager;

        defaultCurrency = totalEconomy.getDefaultCurrency();
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {

        Optional<UserStorageService> userStorage = Sponge.getServiceManager().provide(UserStorageService.class);

        Optional<String> playerName = args.getOne("playerName");
        if(playerName.isPresent() && src.hasPermission("totaleconomy.command.balance.other")){
            Optional<User> user = userStorage.get().get(playerName.get());

            if (!user.isPresent()){
                src.sendMessage(Text.of("O jogador " + playerName.get() + " não existe!"));
                return CommandResult.success();
            }

            TEAccount playerAccount = (TEAccount) accountManager.getOrCreateAccount(user.get().getUniqueId()).get();

            String moneyValue = defaultCurrency.format(playerAccount.getBalance(defaultCurrency)).toPlain();

            src.sendMessage(Text.of("§aO saldo do jogador §e" + user.get().getName() + "§a é §6" + moneyValue ));
        }else {

            if ( !(src instanceof Player)){
                src.sendMessage(Text.of("§aUse /bal <playerName>"));
                return CommandResult.success();
            }
            Player player = (Player) src;
            TEAccount playerAccount = (TEAccount) accountManager.getOrCreateAccount(player.getUniqueId()).get();
            String moneyValue = defaultCurrency.format(playerAccount.getBalance(defaultCurrency)).toPlain();
            src.sendMessage(Text.of("§aO seu saldo atual é: §6" + moneyValue ));
        }
        return CommandResult.success();
    }
}
