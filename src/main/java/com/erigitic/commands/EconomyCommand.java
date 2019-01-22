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

import com.erigitic.FCSpongeUtil;
import com.erigitic.config.AccountManager;
import com.erigitic.config.TEAccount;
import com.erigitic.main.TotalEconomy;
import com.erigitic.util.MessageManager;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.text.Text;

import java.math.BigDecimal;
import java.util.List;

public class EconomyCommand implements CommandExecutor {
    private TotalEconomy totalEconomy;
    private AccountManager accountManager;
    private MessageManager messageManager;
    private Currency defaultCurrency;
    private Cause cause;

    public EconomyCommand(TotalEconomy totalEconomy, AccountManager accountManager, MessageManager messageManager) {
        this.totalEconomy = totalEconomy;
        this.accountManager = accountManager;
        this.messageManager = messageManager;

        defaultCurrency = totalEconomy.getDefaultCurrency();

        cause = Cause.builder()
                .append(totalEconomy.getPluginContainer())
                .build(EventContext.empty());
    }

    private enum OperationType {
        GIVE,
        TAKE,
        SET;
    }

    @Override
    public CommandResult execute(CommandSource sender, CommandContext args) throws CommandException {

        if (!FCSpongeUtil.hasThePermission(sender,"totaleconomy.command.eco")){
            return CommandResult.success();
        }

        //Passando os argumentos para um ArrayList
        List<String> argumentos = FCSpongeUtil.parseSpongeArgsToList(args,4);

        switch (argumentos.get(0).toLowerCase()){
            case "help":
            case "?":
            case "":
                return help(sender,argumentos);
            case "give":
                return operation(sender,argumentos,OperationType.GIVE);
            case "take":
                return operation(sender,argumentos,OperationType.TAKE);
            case "set":
                return operation(sender,argumentos,OperationType.SET);
        }

        sender.sendMessage(Text.of("§cErro de parametros, por favor use §e/eco help"));

        return CommandResult.success();
    }


    // -----------------------------------------------------------------------------------------------------------------------------//
    // Command Help
    // -----------------------------------------------------------------------------------------------------------------------------//
    public CommandResult help(CommandSource sender, List<String> argumentos){
        sender.sendMessage(Text.of("§6§m--------------------§6( §aEconomy §6)§m---------------------"));

        sender.sendMessage(Text.of("§3§l ▶ §a/eco give <playerName> <amout>"));
        sender.sendMessage(Text.of("§3§l ▶ §a/eco take <playerName> <amout>"));
        sender.sendMessage(Text.of("§3§l ▶ §a/eco set <playerName> <amout>"));

        sender.sendMessage(Text.of(""));
        sender.sendMessage(Text.of("§6§m-----------------------------------------------------"));
        return CommandResult.success();
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Command Help
    // -----------------------------------------------------------------------------------------------------------------------------//
    public CommandResult operation(CommandSource sender, List<String> argumentos, OperationType operationType){


        if (argumentos.get(1).isEmpty() || argumentos.get(2).isEmpty()){
            switch (operationType){
                case GIVE:
                    sender.sendMessage(Text.of("§3§l ▶ §a/eco give <playerName> <amout>"));
                    break;
                case TAKE:
                    sender.sendMessage(Text.of("§3§l ▶ §a/eco take <playerName> <amout>"));
                    break;
                case SET:
                    sender.sendMessage(Text.of("§3§l ▶ §a/eco set <playerName> <amout>"));
                    break;
            }
            return help(sender,argumentos);
        }

        User offlinePlayer = FCSpongeUtil.getOfflinePlayer(argumentos.get(1));

        if (offlinePlayer == null){
            sender.sendMessage(Text.of("§4§l ▶ §cNão existe nenhum jogador chamado " + argumentos.get(1)));
            return CommandResult.success();
        }

        Double moneyAmout = parseStringToDouble(argumentos.get(2));

        if (moneyAmout == null){
            sender.sendMessage(Text.of("§4§l ▶ §cErro de parâmestros... \'" + argumentos.get(2) + "\' não é um número válido!"));
            return CommandResult.success();
        }


        BigDecimal amount = new BigDecimal(moneyAmout).setScale(2, BigDecimal.ROUND_DOWN);
        TEAccount recipientAccount = (TEAccount) accountManager.getOrCreateAccount(offlinePlayer.getUniqueId()).get();

        switch (operationType){
            case GIVE:
                recipientAccount.deposit(defaultCurrency, amount, cause);
                sender.sendMessage(Text.of("§2§l ▶ §aO jogador §e" + argumentos.get(2) + "§a teve §6$" + amount + "§a adicionado(s) a sua conta!"));
                break;
            case TAKE:
                recipientAccount.withdraw(defaultCurrency, amount, cause);
                sender.sendMessage(Text.of("§2§l ▶ §aO jogador §e" + argumentos.get(2) + "§a teve §6$" + amount + "§a retirado(s) da sua conta!"));
                break;
            case SET:
                recipientAccount.setBalance(defaultCurrency, amount, cause);
                sender.sendMessage(Text.of("§2§l ▶ §aO saldo do jogador §e" + argumentos.get(2) + "§a foi definido para: §6$" + amount));
                break;
        }
        return CommandResult.success();
    }


    private Double parseStringToDouble(String string){
        try {
            Double aDouble = Double.parseDouble(string);
            return aDouble;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
