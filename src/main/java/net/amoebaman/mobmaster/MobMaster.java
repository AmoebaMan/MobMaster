package net.amoebaman.mobmaster;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.entity.Skeleton.SkeletonType;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.google.common.collect.Lists;

import net.amoebaman.amoebautils.CommandController;
import net.amoebaman.amoebautils.CommandController.CommandHandler;
import net.amoebaman.amoebautils.chat.Chat;
import net.amoebaman.amoebautils.chat.Scheme;
import net.amoebaman.amoebautils.maps.PlayerMap;
import net.amoebaman.amoebautils.plugin.MetricsLite;
import net.amoebaman.mobmaster.MobFlags.ArmorType;

public class MobMaster extends JavaPlugin implements Listener{
    
    /**
     * All male names that can be assigned to mobs. Loaded from .jar on enable.
     */
    private List<String> boyNames = new ArrayList<String>();
    
    /**
     * All female names that can be assigned to mobs. Loaded from .jar on
     * enable.
     */
    private List<String> girlNames = new ArrayList<String>();
    
    /**
     * Maps players to their spawn bindings, making wand-spawning possible.
     */
    private PlayerMap<Map<ItemStack, String>> binds = new PlayerMap<Map<ItemStack, String>>();
    
    public void onEnable(){
        /*
         * Write our extremely limited config, just in case it doesn't exist
         */
        try{
            getConfig().options().copyDefaults(true);
            getConfig().save("plugins/MobMaster/config.yml");
        }
        catch(Exception e){
            e.printStackTrace();
        }
        /*
         * Load in mob name lists from resource files
         */
        Scanner m = new Scanner(MobMaster.plugin().getResource("names_male.txt"));
        m.useDelimiter("\\n");
        while(m.hasNext())
            boyNames.add(m.next());
        m.close();
        Scanner f = new Scanner(MobMaster.plugin().getResource("names_female.txt"));
        f.useDelimiter("\\n");
        while(f.hasNext())
            girlNames.add(f.next());
        f.close();
        /*
         * Schedule repeating tasks for projectile mobs and overlord mobs
         */
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new ShooterMobs(), 0L, 4L);
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new OverlordMobs(), 0L, 100L);
        /*
         * Register events and commands for fun and profit
         */
        Bukkit.getPluginManager().registerEvents(this, this);
        CommandController.registerCommands(this);
        /*
         * Start up PluginMetrics (if enabled in the config)
         */
        if(getConfig().getBoolean("enable-metrics")){
            try{
                new MetricsLite(this).start();
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    public static JavaPlugin plugin(){
        return (JavaPlugin) Bukkit.getPluginManager().getPlugin("MobMaster");
    }
    
    @SuppressWarnings("deprecation")
    @CommandHandler(cmd = "mobspawn")
    public void mobspawn_cmd(final CommandSender sender, String[] args){
        /*
         * We can't do anything if there haven't been any arguments passed
         */
        if(args.length < 1){
            sender.sendMessage(Chat.format("&xInclude at least the mob type to spawn", Scheme.ERROR));
            return;
        }
        /*
         * Begin parsing up the arguments
         */
        MobFlags flags = new MobFlags();
        if(args[0].contains(":")){
            flags.tag = args[0].split(":")[1];
            args[0] = args[0].split(":")[0];
        }
        /*
         * Overlord condition
         */
        if(flags.tag.contains("over"))
            flags.overlord = true;
        /*
         * Get the type of mob we're spawning and make sure it's valid
         */
        EntityType type = Utils.matchName(args[0]);
        if(type == null || !type.isAlive() || !type.isSpawnable()){
            sender.sendMessage(Chat.format("&xMob type not recognized or not spawnable)", Scheme.ERROR));
            return;
        }
        /*
         * Get the place to spawn the mobs
         * This can be either the player's location, their targeted location, or
         * a coordinate-specified location
         */
        Location loc = null;
        if(sender instanceof Player)
            loc = ((Player) sender).getLastTwoTargetBlocks((HashSet<Byte>) null, 250).get(0).getLocation().add(0.5, 0.5, 0.5);
        if(sender instanceof BlockCommandSender)
            loc = ((BlockCommandSender) sender).getBlock().getLocation().add(0.5, 1.5, 0.5);
        if(sender instanceof CommandMinecart)
            loc = ((CommandMinecart) sender).getLocation();
        /*
         * Figure out how many mobs to spawn (1 unless otherwise specified)
         */
        int amount = 1;
        if(args.length >= 2){
            try{
                amount = Integer.parseInt(args[1]);
            }
            catch(NumberFormatException nfe){}
        }
        /*
         * Begin parsing the remainder of the arguments (flexibly)
         */
        SpawnMode mode = SpawnMode.STATIC;
        if(args.length >= 3)
            for(int i = 2; i < args.length; i++){
                /*
                 * Split each argument into a flag and value, if possible
                 */
                String flag = args[i].contains(":") ? args[i].substring(0, args[i].indexOf(':')) : args[i];
                String value = args[i].contains(":") ? args[i].substring(args[i].indexOf(':') + 1) : "";
                /*
                 * Process the "name" (or just "n") flag
                 * Further splits the flag value by commas and adds each item to
                 * the pool of names that this spawn will randomly select from
                 */
                if(flag.startsWith("n"))
                    flags.name.addAll(Lists.newArrayList(value.replace('_', ' ').split(",")));
                /*
                 * Process flags that correspond to potion effects
                 * Their values will be the strengths of those effects
                 */
                PotionEffectType effect = Utils.matchPotionEffect(flag);
                if(effect != null){
                    try{
                        flags.effects.put(effect, Integer.parseInt(value));
                    }
                    catch(Exception e){}
                }
                /*
                 * Process the "mode" (or just "m") flag
                 * Either "static", "bomb", "fountain", or "chain"
                 */
                if(flag.startsWith("m")){
                    mode = SpawnMode.valueOf(value.toUpperCase());
                    if(mode == null)
                        mode = SpawnMode.STATIC;
                }
                /*
                 * Process the "health" (or just "h") flag
                 * A decimal value to multiply the mobs' health by
                 */
                if(flag.startsWith("h")){
                    try{
                        flags.health = Float.parseFloat(value);
                    }
                    catch(Exception e){}
                }
                /*
                 * Process the "damage" (or just "d") flag
                 * A decimal value to multiply the mobs' damage output by
                 */
                if(flag.startsWith("d")){
                    try{
                        flags.damage = Float.parseFloat(value);
                    }
                    catch(Exception e){}
                }
                /*
                 * Process the "target" (or just "t") flag
                 * Either a player's name (to spawn on that player), or a world
                 * name and three integers separated by commas (to spawn at a
                 * location)
                 */
                if(flag.startsWith("t")){
                    if(Bukkit.getPlayer(value) != null)
                        loc = Bukkit.getPlayer(value).getLocation();
                    else{
                        String[] split = value.split(",");
                        try{
                            if(loc == null)
                                loc = new Location(Bukkit.getWorld(split[0]), Integer.parseInt(split[1]) + 0.5, Integer.parseInt(split[2]) + 0.5, Integer.parseInt(split[3]) + 0.5);
                            else{
                                loc.setX(Integer.parseInt(split[0]) + 0.5);
                                loc.setY(Integer.parseInt(split[1]) + 0.5);
                                loc.setZ(Integer.parseInt(split[2]) + 0.5);
                            }
                        }
                        catch(Exception e){}
                    }
                }
                /*
                 * Process the "explosive" (or just "e") flag
                 * A decimal value to indicate the explosive power of the mob on
                 * death, optionally followed by the character 'f' to make the
                 * explosion incendiary
                 */
                if(flag.startsWith("e")){
                    if(value.contains("f")){
                        flags.incendiary = true;
                        value = value.replace("f", "");
                    }
                    try{
                        flags.explosion = Float.parseFloat(value);
                    }
                    catch(Exception e){}
                }
                /*
                 * Process the "armor" (or just "a") flag
                 * Any rank of armor, from "leather" up to "diamond"
                 */
                if(flag.startsWith("a"))
                    flags.armor = ArmorType.fromString(value);
                /*
                 * Process the "shoot" (or just "s") flag
                 * The entity name of any projectile for the mob to shoot and
                 * its rate of fire (lower is faster) separated by a comma
                 */
                if(flag.startsWith("s")){
                    String[] split = value.split(",");
                    if(split.length >= 2)
                        try{
                            flags = flags.withProjectile(Utils.matchName(split[0]), Integer.parseInt(split[1]));
                        }
                        catch(Exception e){}
                }
                /*
                 * Process the "counter" (or just "c") flag
                 * A decimal value to indicate what percentage of damage to
                 * reflect on the dealer
                 */
                if(flag.startsWith("c"))
                    try{
                        flags.counter = Float.parseFloat(value);
                    }
                    catch(Exception e){}
                /*
                 * Process the "bind" flag
                 * This will bind the currently executing command string to the
                 * held item, allowing the player to use it rapidly with ease in
                 * the future
                 */
                if(flag.equals("bind") && sender instanceof Player && ((Player) sender).getItemInHand() != null){
                    Player player = (Player) sender;
                    
                    String fullCmd = "mobspawn " + type.name() + (flags.tag.isEmpty() ? "" : ":" + flags.tag) + " " + amount;
                    for(String arg : args)
                        fullCmd += " " + arg;
                    if(!binds.containsKey(player))
                        binds.put(player, new HashMap<ItemStack, String>());
                    
                    binds.get(player).put(player.getItemInHand(), fullCmd);
                }
                /*
                 * Process the "overlord" (or just "o") flag
                 * If present, mobs spawned will be overlords
                 */
                if(flag.startsWith("o"))
                    flags.overlord = true;
            }
        /*
         * Can't spawn if we don't have a place to dump the mobs
         */
        if(loc == null){
            sender.sendMessage(Chat.format("&xSorry guy, you can't spawn mobs without a reference location", Scheme.ERROR));
            sender.sendMessage(Chat.format("&xIf you're on console, try adding &z\"t:<player>\"&x or &z\"t:(<x>,<y>,<z>)\"&x to the command", Scheme.ERROR));
            return;
        }
        /*
         * Setup final variables in case we need to do a time-distributed spawn
         */
        final EntityType fType = type;
        final Location fLoc = loc;
        final MobFlags fFlags = flags;
        /*
         * Execute the spawn based on the selected mode
         */
        switch(mode){
            case STATIC:
                /*
                 * Static mode just dumps the mobs all in one place
                 */
                for(int i = 0; i < amount; i++)
                    spawnMob(loc, new Vector(0, 0, 0), type, flags);
                break;
            case BOMB:
                /*
                 * Bomb mode kind of tosses the mobs around when they spawn
                 */
                for(int i = 0; i < amount; i++)
                    spawnMob(loc, new Vector(Math.random() - 0.5, Math.random() * 0.75, Math.random() - 0.5), type, flags);
                break;
            case CHAIN:
                /*
                 * Chain mode spawns mobs continually at a rate of 4-per-second
                 * at the player's cursor location, which can be moved
                 */
                for(int i = 0; i < amount; i++)
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
                        
                        public void run(){
                            Location target = fLoc;
                            if(sender instanceof Player)
                                target = ((Player) sender).getTargetBlock((HashSet<Byte>) null, 250).getLocation();
                            spawnMob(target, new Vector(0, 0, 0), fType, fFlags);
                        }
                    }, i * 5);
                break;
            case FOUNTAIN:
                /*
                 * Fountain mode spawns mobs continually at a rate of
                 * 4-per-second from a fixed position
                 */
                for(int i = 0; i < amount; i++)
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
                        
                        public void run(){
                            spawnMob(fLoc, new Vector(Math.random() - 0.5, Math.random() * 0.75, Math.random() - 0.5), fType, fFlags);
                        }
                    }, i * 5);
                break;
        }
        
    }
    
    @SuppressWarnings("deprecation")
    @CommandHandler(cmd = "mobkill")
    public void mobkill_cmd(CommandSender sender, String[] args){
        Location loc = null;
        if(sender instanceof Player)
            loc = ((Player) sender).getLastTwoTargetBlocks((HashSet<Byte>) null, 250).get(0).getLocation().add(0.5, 0.5, 0.5);
        if(sender instanceof BlockCommandSender)
            loc = ((BlockCommandSender) sender).getBlock().getLocation().add(0.5, 1.5, 0.5);
        if(sender instanceof CommandMinecart)
            loc = ((CommandMinecart) sender).getLocation();
        
        int radius = 0;
        Map<EntityType, Boolean> which = new HashMap<EntityType, Boolean>();
        boolean monsters = true, animals = false, villagers = false, others = false;
        
        if(args.length >= 1)
            for(int i = 0; i < args.length; i++){
                String flag = args[i].contains(":") ? args[i].substring(0, args[i].indexOf(':')) : args[i];
                String value = args[i].contains(":") ? args[i].substring(args[i].indexOf(':') + 1) : "";
                if(flag.startsWith("r")){
                    try{
                        radius = Integer.parseInt(value);
                    }
                    catch(Exception e){}
                }
                if(flag.startsWith("t")){
                    if(Bukkit.getPlayer(value) != null)
                        loc = Bukkit.getPlayer(value).getLocation();
                    else{
                        String[] split = value.split(",");
                        try{
                            if(loc == null)
                                loc = new Location(Bukkit.getWorld(split[0]), Integer.parseInt(split[1]) + 0.5, Integer.parseInt(split[2]) + 0.5, Integer.parseInt(split[3]) + 0.5);
                            else{
                                loc.setX(Integer.parseInt(split[0]) + 0.5);
                                loc.setY(Integer.parseInt(split[1]) + 0.5);
                                loc.setZ(Integer.parseInt(split[2]) + 0.5);
                            }
                        }
                        catch(Exception e){}
                    }
                }
                if(flag.startsWith("+")){
                    if(flag.contains("m"))
                        monsters = true;
                    if(flag.contains("a"))
                        animals = true;
                    if(flag.contains("v"))
                        villagers = true;
                    if(flag.contains("o"))
                        others = true;
                }
                if(flag.startsWith("-")){
                    if(flag.contains("m"))
                        monsters = false;
                    if(flag.contains("a"))
                        animals = false;
                    if(flag.contains("v"))
                        villagers = false;
                    if(flag.contains("o"))
                        others = false;
                }
                if(Utils.matchName(flag) != null){
                    try{
                        which.put(Utils.matchName(flag), Boolean.parseBoolean(value));
                    }
                    catch(Exception e){}
                }
            }
        
        if(loc == null){
            sender.sendMessage(Chat.format("&xSorry guy, you can't slaughter mobs without a reference location", Scheme.ERROR));
            sender.sendMessage(Chat.format("&xIf you're on console, try adding &z\"t:<player>\"&x or &z\"t:(<x>,<y>,<z>)\"&x to the command", Scheme.ERROR));
            return;
        }
        
        int count = 0;
        for(LivingEntity e : loc.getWorld().getEntitiesByClass(LivingEntity.class))
            if(!(e instanceof Player))
                if(radius == 0 ^ e.getLocation().distance(loc) < radius){
                    boolean remove;
                    if(which.containsKey(e.getType()))
                        remove = which.get(e.getType());
                    else
                        remove = e instanceof Monster && monsters || e instanceof Animals && animals || e instanceof Villager && villagers || others && !(e instanceof Monster) && !(e instanceof Animals) && !(e instanceof Villager);
                    if(remove){
                        e.remove();
                        count++;
                    }
                }
        sender.sendMessage(Chat.format("&xSlaughtered &z" + count + "&x mobs", Scheme.NORMAL));
    }
    
    public void spawnMob(Location loc, Vector vel, EntityType type, MobFlags flags){
        if(!(type.isSpawnable() && type.isAlive()))
            return;
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc.clone().add((Math.random() - 0.5) * 0.1, 0, (Math.random() - 0.5) * 0.1), type);
        e.setCanPickupItems(true);
        if(vel.length() > 0)
            e.setVelocity(vel);
        if(!flags.name.isEmpty()){
            e.setCustomName(flags.name.get(new Random().nextInt(flags.name.size())));
            if(e.getCustomName().contains("rand")){
                List<String> options;
                if(e.getCustomName().contains("b") ^ e.getCustomName().contains("g"))
                    options = e.getCustomName().contains("b") ? boyNames : girlNames;
                else
                    options = Math.random() > 0.5 ? boyNames : girlNames;
                e.setCustomName(options.get(new Random().nextInt(options.size())));
            }
            e.setCustomNameVisible(true);
            e.setRemoveWhenFarAway(false);
        }
        if(e instanceof Ageable && flags.tag.contains("baby")){
            ((Ageable) e).setBaby();
            ((Ageable) e).setAgeLock(true);
        }
        if(e instanceof Zombie){
            if(flags.tag.contains("baby"))
                ((Zombie) e).setBaby(true);
            if(flags.tag.contains("vill"))
                ((Zombie) e).setVillager(true);
        }
        if(e instanceof Creeper && (flags.tag.contains("charged") || flags.tag.contains("powered")))
            ((Creeper) e).setPowered(true);
        if(e instanceof Pig && flags.tag.contains("saddle"))
            ((Pig) e).setSaddle(true);
        if(e instanceof PigZombie && flags.tag.contains("angry"))
            ((PigZombie) e).setAngry(true);
        if(e instanceof Wolf && flags.tag.contains("angry"))
            ((Wolf) e).setAngry(true);
        if(e instanceof Sheep){
            if(flags.tag.contains("shear"))
                ((Sheep) e).setSheared(true);
            if(flags.tag.contains("rand"))
                ((Sheep) e).setColor(DyeColor.values()[new Random().nextInt(DyeColor.values().length)]);
            else
                for(DyeColor color : DyeColor.values())
                    if(flags.tag.contains(color.name().toLowerCase()))
                        ((Sheep) e).setColor(color);
        }
        if(e instanceof Skeleton && flags.tag.contains("wither"))
            ((Skeleton) e).setSkeletonType(SkeletonType.WITHER);
        if(e instanceof Slime){
            try{
                ((Slime) e).setSize(Integer.parseInt(flags.tag));
            }
            catch(Exception ex){}
        }
        if(e instanceof Villager){
            if(flags.tag.contains("rand"))
                ((Villager) e).setProfession(Profession.values()[new Random().nextInt(Profession.values().length)]);
            else{
                try{
                    ((Villager) e).setProfession(Profession.valueOf(flags.tag.toUpperCase()));
                }
                catch(Exception ex){}
            }
        }
        if(e instanceof Guardian && flags.tag.contains("elder"))
            ((Guardian) e).setElder(true);
        if(e instanceof Rabbit)
            for(Rabbit.Type rtype : Rabbit.Type.values())
                if(flags.tag.contains(rtype.name().toLowerCase()))
                    ((Rabbit) e).setRabbitType(rtype);
        
        if(flags.armor != null){
            EntityEquipment equip = e.getEquipment();
            equip.setHelmet(new ItemStack(Material.matchMaterial(flags.armor.name() + "_HELMET")));
            equip.setChestplate(new ItemStack(Material.matchMaterial(flags.armor.name() + "_CHESTPLATE")));
            equip.setLeggings(new ItemStack(Material.matchMaterial(flags.armor.name() + "_LEGGINGS")));
            equip.setBoots(new ItemStack(Material.matchMaterial(flags.armor.name() + "_BOOTS")));
        }
        
        for(Entry<PotionEffectType, Integer> entry : flags.effects.entrySet())
            e.addPotionEffect(new PotionEffect(entry.getKey(), Integer.MAX_VALUE, entry.getValue()));
        
        e.setMaxHealth(e.getMaxHealth() * flags.health);
        e.setHealth(e.getMaxHealth());
        
        e.setMetadata("mob-flags", new FixedMetadataValue(this, flags));
    }
    
    @EventHandler
    public void mobspawnCommandBinds(PlayerInteractEvent event){
        Player player = event.getPlayer();
        if(event.getAction() == Action.RIGHT_CLICK_AIR && binds.containsKey(player) && binds.get(player).containsKey(event.getItem()))
            player.performCommand(binds.get(player).get(event.getItem()));
    }
    
    @EventHandler
    public void applyDamageModifier(final EntityDamageEvent event){
        if(event instanceof EntityDamageByEntityEvent){
            Entity e = ((EntityDamageByEntityEvent) event).getDamager();
            if(e instanceof Projectile && ((Projectile) e).getShooter() instanceof Entity)
                e = (Entity) ((Projectile) e).getShooter();
            if(e instanceof LivingEntity && Utils.isMasterMob(e))
                event.setDamage(event.getDamage() * ((MobFlags) e.getMetadata("mob-flags").get(0).value()).damage);
            final Entity d = e;
            final Entity v = event.getEntity();
            if(Utils.isMasterMob(v) && d instanceof LivingEntity)
                Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
                    
                    public void run(){
                        ((LivingEntity) d).damage(event.getDamage() * ((MobFlags) v.getMetadata("mob-flags").get(0).value()).counter, v);
                    }
                }, 2L);
        }
    }
    
    @EventHandler
    public void combustibleMobs(EntityDeathEvent event){
        LivingEntity e = event.getEntity();
        if(Utils.isMasterMob(e)){
            MobFlags flags = (MobFlags) e.getMetadata("mob-flags").get(0).value();
            if(flags.explosion > 0)
                e.getWorld().createExplosion(e.getLocation(), flags.explosion, flags.incendiary);
        }
    }
    
    public void zombieOverlord(EntityDeathEvent event){
        final LivingEntity e = event.getEntity();
        for(Entity other : e.getNearbyEntities(5, 5, 5))
            if(other.getType() == EntityType.ZOMBIE && Utils.isMasterMob(other)){
                final MobFlags flags = (MobFlags) other.getMetadata("mob-flags").get(0).value();
                if(flags.overlord)
                    Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable(){
                        
                        public void run(){
                            Entity zombie = e.getWorld().spawnEntity(e.getLocation(), EntityType.ZOMBIE);
                            if(e.getType() == EntityType.PLAYER)
                                zombie.setMetadata("mob-flags", new FixedMetadataValue(MobMaster.plugin(), new MobFlags().asOverlord()));
                        }
                    }, 50);
            }
    }
    
    public static class ShooterMobs implements Runnable{
        
        static int tick = 0;
        
        public void run(){
            
            for(World world : Bukkit.getWorlds())
                for(final LivingEntity e : world.getLivingEntities())
                    if(Utils.isMasterMob(e)){
                        final MobFlags flags = (MobFlags) e.getMetadata("mob-flags").get(0).value();
                        if(flags.projectile != null && flags.rateOfFire > 0 && tick % flags.rateOfFire == 0)
                            Bukkit.getScheduler().scheduleSyncDelayedTask(MobMaster.plugin(), new Runnable(){
                                
                                public void run(){
                                    e.launchProjectile((Class<? extends Projectile>) flags.projectile.getEntityClass());
                                }
                            }, (long) (Math.random() * 4));
                    }
            
            tick++;
            if(tick > 50)
                tick = 0;
            
        }
        
    }
    
    /*
     * THIS PROCESS IS CALLED EVERY 5 SECONDS
     */
    public static class OverlordMobs implements Runnable{
        
        public void run(){
            
            for(World world : Bukkit.getWorlds())
                for(final LivingEntity e : world.getLivingEntities())
                    if(Utils.isMasterMob(e)){
                        final MobFlags flags = (MobFlags) e.getMetadata("mob-flags").get(0).value();
                        if(flags.overlord)
                            Bukkit.getScheduler().runTaskLater(plugin(), new Runnable(){
                                
                                public void run(){
                                    switch(e.getType()){
                                        case BAT:
                                            /*
                                             * Bats give off shrieks that
                                             * temporarily blind players
                                             */
                                            for(int i = 0; i < 20; i++){
                                                final int j = 20 - i;
                                                Bukkit.getScheduler().runTaskLater(plugin(), new Runnable(){
                                                    
                                                    public void run(){
                                                        e.getWorld().playSound(e.getLocation(), Sound.BAT_DEATH, j / 20, j / 20);
                                                    }
                                                }, i);
                                            }
                                            for(Entity other : e.getNearbyEntities(5, 5, 5))
                                                if(other instanceof Player && ((Player) other).getGameMode() != GameMode.CREATIVE)
                                                    ((Player) other).addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                                            break;
                                        case BLAZE:
                                            break;
                                        case CAVE_SPIDER:
                                            /*
                                             * Cave spiders spin webs around
                                             * them
                                             */
                                            for(int x = -2; x <= 2; x++)
                                                for(int y = -1; y <= 1; y++)
                                                    for(int z = -2; z <= 2; z++)
                                                        if(Math.random() > 0.3){
                                                            final Block target = e.getLocation().getBlock().getRelative(x, y, z);
                                                            if(target.isEmpty()){
                                                                target.setType(Material.WEB);
                                                                Bukkit.getScheduler().runTaskLater(plugin(), new Runnable(){
                                                                    
                                                                    public void run(){
                                                                        target.setType(Material.AIR);
                                                                    }
                                                                }, (long) (Math.random() * 50 + 50));
                                                            }
                                                        }
                                            break;
                                        case CREEPER:
                                            break;
                                        case ENDERMAN:
                                            /*
                                             * Endermen fuck with the
                                             * perspective of
                                             * nearby players (change pitch/yaw)
                                             */
                                            for(Entity other : e.getNearbyEntities(5, 5, 5))
                                                if(other instanceof Player && ((Player) other).getGameMode() != GameMode.CREATIVE){
                                                    Location loc = other.getLocation();
                                                    loc.setYaw((float) (Math.random() * 360));
                                                    loc.setPitch((float) (Math.random() * 360));
                                                    other.teleport(loc);
                                                }
                                            break;
                                        case ENDER_DRAGON:
                                            break;
                                        case GHAST:
                                            break;
                                        case GIANT:
                                            /*
                                             * Giants kick players near them
                                             * away
                                             */
                                            for(Entity other : e.getNearbyEntities(5, 5, 5))
                                                if(other instanceof Player && ((Player) other).getGameMode() != GameMode.CREATIVE){
                                                    ((Player) other).playSound(e.getLocation(), Sound.ZOMBIE_DEATH, 1, 0.25f);
                                                    ((Player) other).damage(2, e);
                                                    Vector v = new Vector(2f / (other.getLocation().getX() - e.getLocation().getX()), Math.random() + 0.25, 2f / (other.getLocation().getZ() - e.getLocation().getZ()));
                                                    e.setVelocity(v);
                                                }
                                            break;
                                        case MAGMA_CUBE:
                                        case SLIME:
                                            /*
                                             * Slimes and magma cubes multiply
                                             */
                                            if(Math.random() > 0.75)
                                                e.getWorld().spawnEntity(e.getLocation(), e.getType());
                                            break;
                                        case MUSHROOM_COW:
                                            break;
                                        case PIG_ZOMBIE:
                                            break;
                                        case SHEEP:
                                            break;
                                        case SILVERFISH:
                                            break;
                                        case SKELETON:
                                            break;
                                        case SNOWMAN:
                                            break;
                                        case SPIDER:
                                            break;
                                        case SQUID:
                                            break;
                                        case WITCH:
                                            break;
                                        case WITHER:
                                            break;
                                        case ZOMBIE:
                                            break;
                                        /*
                                         * Nonaggressive entities obviously have
                                         * no overlord behavior.
                                         */
                                        case CHICKEN:
                                        case COW:
                                        case HORSE:
                                        case IRON_GOLEM:
                                        case OCELOT:
                                        case PIG:
                                        case PLAYER:
                                        case VILLAGER:
                                        case WOLF:
                                            break;
                                        /*
                                         * Nonliving entities can't do SHIT. We
                                         * leave these here so we can omit the
                                         * default statement so that Eclipse
                                         * will warn us when new entities are
                                         * added if we miss them.
                                         */
                                        case ARROW:
                                        case ARMOR_STAND:
                                        case BOAT:
                                        case COMPLEX_PART:
                                        case DROPPED_ITEM:
                                        case EGG:
                                        case ENDER_CRYSTAL:
                                        case ENDER_PEARL:
                                        case ENDER_SIGNAL:
                                        case ENDERMITE:
                                        case EXPERIENCE_ORB:
                                        case FALLING_BLOCK:
                                        case FIREBALL:
                                        case FIREWORK:
                                        case FISHING_HOOK:
                                        case GUARDIAN:
                                        case ITEM_FRAME:
                                        case LEASH_HITCH:
                                        case LIGHTNING:
                                        case MINECART:
                                        case MINECART_CHEST:
                                        case MINECART_COMMAND:
                                        case MINECART_FURNACE:
                                        case MINECART_HOPPER:
                                        case MINECART_MOB_SPAWNER:
                                        case MINECART_TNT:
                                        case PAINTING:
                                        case PRIMED_TNT:
                                        case RABBIT:
                                        case SMALL_FIREBALL:
                                        case SNOWBALL:
                                        case SPLASH_POTION:
                                        case THROWN_EXP_BOTTLE:
                                        case UNKNOWN:
                                        case WEATHER:
                                        case WITHER_SKULL:
                                            break;
                                    }
                                }
                            }, (long) (Math.random() * 20));
                    }
            
        }
        
    }
    
}
