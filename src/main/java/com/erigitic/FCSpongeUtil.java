package com.erigitic;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class FCSpongeUtil {

    /**
     *   Transforma os argumentos inseridos em um Arraylist!
     */
    public static List<String> parseSpongeArgsToList(CommandContext args, int numOfArgs){
        String allArgs = "";
        if(args.getOne("allArgs").isPresent()){
            allArgs = (String) args.getOne("allArgs").get();
        }
        String[] splitedArgs = allArgs.split(" ");
        List<String> argumentos = new ArrayList<String>();
        for (int i = 0; i < numOfArgs; i++) {
            if (i < splitedArgs.length)
                argumentos.add(splitedArgs[i]);
            else
                argumentos.add("");
        }
        return argumentos;
    }


    public static boolean hasThePermission(CommandSource player, String permission) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(Text.of("§cVocê não tem permissão " + permission + " para fazer isto."));
            return false;
        } else {
            return true;
        }
    }

    public static UserStorageService spongeUserStorage = Sponge.getServiceManager().provide(UserStorageService.class).get();
    public static User getOfflinePlayer(String playerName) {
        Optional<User> optionalUser = spongeUserStorage.get(playerName);
        return optionalUser.isPresent() ? optionalUser.get() : null;
    }
}
