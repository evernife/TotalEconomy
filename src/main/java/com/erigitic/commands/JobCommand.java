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
import com.erigitic.jobs.Job;
import com.erigitic.jobs.JobBasedRequirement;
import com.erigitic.jobs.JobManager;
import com.erigitic.jobs.JobAction;
import com.erigitic.jobs.JobActionReward;
import com.erigitic.jobs.JobSet;
import com.erigitic.main.TotalEconomy;
import com.erigitic.util.MessageManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.erigitic.util.StringUtil;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.economy.Currency;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

public class JobCommand implements CommandExecutor {

    private final TotalEconomy totalEconomy;
    private final AccountManager accountManager;
    private final JobManager jobManager;
    private final MessageManager messageManager;

    public JobCommand() {
        totalEconomy = TotalEconomy.getInstance();
        accountManager = totalEconomy.getAccountManager();
        jobManager = totalEconomy.getJobManager();
        messageManager = totalEconomy.getMessageManager();
    }

    public CommandSpec commandSpec() {
        Set jobSetCommand = new Set();
        Info jobInfoCommand = new Info();
        Reload jobReloadCommand = new Reload();
        Toggle jobToggleCommand = new Toggle();

        return CommandSpec.builder()
                .child(jobSetCommand.commandSpec(), "set", "s")
                .child(jobInfoCommand.commandSpec(), "info", "i")
                .child(jobReloadCommand.commandSpec(), "reload")
                .child(jobToggleCommand.commandSpec(), "toggle", "t")
                .description(Text.of("Display job information"))
                .permission("totaleconomy.command.job")
                .arguments(GenericArguments.none())
                .executor(this)
                .build();
    }

    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        if (src instanceof Player) {
            Player player = ((Player) src).getPlayer().get();
            TEAccount account = (TEAccount) accountManager.getOrCreateAccount(player.getUniqueId()).get();

            String jobName = account.getCurrentJobName();

            Map<String, String> messageValues = new HashMap<>();
            messageValues.put("job", StringUtil.titleize(jobName));
            messageValues.put("curlevel", String.valueOf(account.getCurrentJobLevel()));
            messageValues.put("curexp", String.valueOf(account.getCurrentJobExp()));
            messageValues.put("exptolevel", String.valueOf(jobManager.getExpToLevel(player)));

            player.sendMessage(messageManager.getMessage("command.job.current", messageValues));
            player.sendMessage(messageManager.getMessage("command.job.level", messageValues));
            player.sendMessage(messageManager.getMessage("command.job.exp", messageValues));
            player.sendMessage(Text.of(TextColors.GRAY, "Available Jobs: ", TextColors.GOLD, jobManager.getJobList()));

            return CommandResult.success();
        } else {
            throw new CommandException(Text.of("You can't have a job!"));
        }
    }

    private class Set implements CommandExecutor {

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Set your job"))
                    .permission("totaleconomy.command.job.set")
                    .executor(this)
                    .arguments(
                            GenericArguments.string(Text.of("jobName")),
                            GenericArguments.optional(
                                    GenericArguments.requiringPermission(
                                        GenericArguments.userOrSource(Text.of("user")),
                                        "totaleconomy.command.job.setother"
                                    )
                            )
                    )
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            String jobName = args.getOne("jobName").get().toString().toLowerCase();
            Optional<User> userOpt = args.getOne("user");

            User user;
            if (userOpt.isPresent()) {
                user = userOpt.get();
            } else if (src instanceof Player) {
                user = (Player) src;
            } else {
                return CommandResult.empty();
            }

            Optional<Job> optJob = jobManager.getJob(jobName, false);
            if (!optJob.isPresent()) {
                throw new CommandException(Text.of("Job " + jobName + " does not exist!"));
            }

            TEAccount account = (TEAccount) accountManager.getOrCreateAccount(user.getUniqueId()).get();

            Job job = optJob.get();
            if (job.getRequirement().isPresent()) {
                JobBasedRequirement req = job.getRequirement().get();

                if (req.getRequiredPermission() != null && !user.hasPermission(req.getRequiredPermission())) {
                    throw new CommandException(Text.of("Not permitted to join job \"", TextColors.GOLD, jobName, TextColors.RED, "\""));
                }

                if (req.getRequiredJob() != null && req.getRequiredJobLevel() > account.getJobLevel(req.getRequiredJob().toLowerCase())) {
                    throw new CommandException(Text.of("Insufficient level! Level ",
                             TextColors.GOLD, req.getRequiredJobLevel(), TextColors.RED," as a ",
                             TextColors.GOLD, req.getRequiredJob(), TextColors.RED, " required!"));
                }
            }

            if (!account.setCurrentJob(jobName)) {
                throw new CommandException(Text.of("Failed to set job. Contact your administrator."));
            } else if (user.getPlayer().isPresent()) {
                Map<String, String> messageValues = new HashMap<>();
                messageValues.put("job", StringUtil.titleize(jobName));

                user.getPlayer().get().sendMessage(messageManager.getMessage("command.job.set", messageValues));
            }

            // Only send additional feedback if CommandSource isn't the target.
            if (!(src instanceof User) || !((User) src).getUniqueId().equals(user.getUniqueId())) {
                src.sendMessage(Text.of(TextColors.GREEN, "Job set."));
            }

            return CommandResult.success();
        }
    }

    private class Info implements CommandExecutor {

        private PaginationService paginationService = Sponge.getServiceManager().provideUnchecked(PaginationService.class);
        private PaginationList.Builder pageBuilder = paginationService.builder();

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Prints out a list of items that reward exp and money for the current job"))
                    .permission("totaleconomy.command.job.info")
                    .executor(this)
                    .arguments(
                        GenericArguments.optional(GenericArguments.flags().flag("e").buildWith(GenericArguments.none())),
                        GenericArguments.optional(GenericArguments.string(Text.of("jobName")))
                    )
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            Optional<String> optJobName = args.getOne("jobName");
            Optional<Job> optJob = Optional.empty();
            String jobName = "";
            boolean extended = args.hasAny("e");

            // Get info on a player's current job
            if (!optJobName.isPresent() && (src instanceof Player)) {
                Player player = (Player) src;

                TEAccount account = (TEAccount) accountManager.getOrCreateAccount(player.getUniqueId()).get();
                optJob = jobManager.getJob(account.getCurrentJobName(), true);
                jobName = account.getCurrentJobName();
            }

            if (optJobName.isPresent()) {
                optJob = jobManager.getJob(optJobName.get().toLowerCase(), false);
                jobName = optJobName.get();
            }

            if (!optJob.isPresent()) {
                throw new CommandException(Text.of(TextColors.RED, "Unknown job: \"" + optJobName.orElse("") + "\""));
            }

            List<Text> lines = new ArrayList();

            for (String s : optJob.get().getSets()) {
                Optional<JobSet> optSet = jobManager.getJobSet(s);

                if (optSet.isPresent()) {
                    JobSet jobSet = optSet.get();

                    for (JobAction action : jobSet.getActions()) {
                        Text listText;

                        if (action.isIdTraited()) {
                            // MC does not support '\t'
                            String tab = new String(new char[action.getAction().length() + 2]).replace("\0", " ");
                            List<Text> texts = new ArrayList<>(action.getRewards().size());

                            action.getRewards().forEach((k, v) -> {
                                Text metaText = Text.of("");

                                if (action.isGrowing() && extended) {
                                    metaText = Text.of(",growing=1");
                                }

                                Text rewardText = Text.of("\n", tab, TextColors.GRAY, "{", action.getIdTrait(), '=', k, metaText, "} ", TextColors.GOLD, formatReward(v));
                                texts.add(rewardText);
                            });

                            listText = Text.join(texts.toArray(new Text[texts.size()]));
                        } else {
                            listText = Text.of(" ", formatReward(action.getReward().get()));

                            if (action.isGrowing() && extended) {
                                listText = Text.of(TextColors.GRAY, "{growing=1}", TextColors.GOLD,  listText);
                            }
                        }

                        lines.add(Text.of(TextColors.GOLD, "[", StringUtil.titleize(action.getAction()), "] ", TextColors.GRAY, action.getTargetId(), TextColors.GOLD, listText));
                    }
                }
            }

            pageBuilder.reset()
                       .header(Text.of(TextColors.GRAY, "Job information for ", TextColors.GOLD, jobName,"\n"))
                       .contents(lines.toArray(new Text[lines.size()]))
                       .build()
                       .sendTo(src);

            return CommandResult.success();
        }
    }

    private Text formatReward(JobActionReward reward) {
        Optional<Currency> rewardCurrencyOpt = Optional.empty();

        if (reward.getCurrencyId() != null) {
            rewardCurrencyOpt = totalEconomy.getTECurrencyRegistryModule().getById("totaleconomy:" + reward.getCurrencyId());
        }

        return Text.of("(", reward.getExpReward(), " EXP) (", rewardCurrencyOpt.orElse(totalEconomy.getDefaultCurrency()).format(new BigDecimal(reward.getMoneyReward())), ")");
    }

    private class Reload implements CommandExecutor {

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Reloads sets and jobs"))
                    .permission("totaleconomy.command.job.reload")
                    .executor(this)
                    .arguments(GenericArguments.none())
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            if (jobManager.reloadJobsAndSets()) {
                src.sendMessage(Text.of(TextColors.GRAY, "[TE] Sets and jobs reloaded."));
            } else {
                throw new CommandException(Text.of(TextColors.RED, "[TE] Failed to reload sets and/or jobs!"));
            }

            return CommandResult.success();
        }
    }

    private class Toggle implements CommandExecutor {

        private final String[] TOGGLE_PLAYER_OPTIONS = {"block-break-info", "block-place-info", "entity-kill-info", "entity-fish-info"};
        private final List<String> TOGGLE_PLAYER_OPTIONS_LIST = Arrays.asList(TOGGLE_PLAYER_OPTIONS);

        public CommandSpec commandSpec() {
            return CommandSpec.builder()
                    .description(Text.of("Toggle job notifications on/off"))
                    .permission("totaleconomy.command.job.toggle")
                    .arguments(
                            GenericArguments.optional(
                                GenericArguments.requiringPermission(
                                    GenericArguments.string(Text.of("option")),
                                    "totaleconomy.command.job.block_info")
                            )
                    )
                    .executor(this)
                    .build();
        }

        @Override
        public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
            if (src instanceof Player) {
                Player sender = (Player) src;
                Optional<String> optionOpt = args.<String>getOne("option");

                if (!optionOpt.isPresent()) {
                    accountManager.toggleNotifications(sender);

                    return CommandResult.success();
                } else {
                    String option = optionOpt.get();
                    int i = TOGGLE_PLAYER_OPTIONS_LIST.indexOf(option);

                    if (i < 0) {
                        throw new CommandException(Text.of("[TE] Unknown option: ", option));
                    }

                    String value = accountManager.getUserOption("totaleconomy:" + option, sender).orElse("0");
                    value = value.equals("0") ? "1" : "0";

                    accountManager.setUserOption("totaleconomy:" + option, sender, value);

                    src.sendMessage(messageManager.getMessage("jobs.toggle"));

                    return CommandResult.success();
                }

            } else {
                throw new CommandException(Text.of("[TE] This command can only be run by a player!"));
            }
        }
    }
}