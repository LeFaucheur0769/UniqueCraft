package fr.plugin.uniquecraft;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;

public class UniqueCraftsPlugin extends JavaPlugin implements Listener {

  private FileConfiguration craftsConfig;
  private File craftsFile;
  private Set<String> globalCraftedItems;

  @Override
  public void onEnable() {
    // Initialisation des données
    globalCraftedItems = new HashSet<>();

    // Chargement de la configuration
    loadConfig();
    loadCraftsConfig();

    // Enregistrement des événements
    getServer().getPluginManager().registerEvents(this, this);

    // Enregistrement de la commande
    getCommand("uniquecraft").setExecutor(this);
    getCommand("uniquecraft").setTabCompleter(this);

    // Chargement des crafts personnalisés
    loadCustomRecipes();

    // Chargement des crafts déjà faits
    loadCraftedItems();

    getLogger().info("Plugin UniqueCrafts activé !");
  }

  @Override
  public void onDisable() {
    saveCraftsConfig();
    getLogger().info("Plugin UniqueCrafts désactivé !");
  }

  private void loadConfig() {
    // Ne pas sauvegarder config.yml automatiquement
    // car nous n'en avons pas besoin
    getConfig().options().copyDefaults(true);
    saveConfig();
  }

  private void loadCraftsConfig() {
    craftsFile = new File(getDataFolder(), "craft.yml");
    if (!craftsFile.exists()) {
      saveResource("craft.yml", false);
    }
    craftsConfig = YamlConfiguration.loadConfiguration(craftsFile);
  }

  private void saveCraftsConfig() {
    try {
      craftsConfig.save(craftsFile);
    } catch (IOException e) {
      getLogger().severe("Erreur lors de la sauvegarde des crafts: " + e.getMessage());
    }
  }

  private void loadCraftedItems() {
    ConfigurationSection craftedSection = craftsConfig.getConfigurationSection("crafted");
    if (craftedSection != null) {
      for (String craftId : craftedSection.getKeys(false)) {
        if (craftedSection.getBoolean(craftId)) {
          globalCraftedItems.add(craftId);
        }
      }
    }
  }

  private void loadCustomRecipes() {
    ConfigurationSection craftsSection = craftsConfig.getConfigurationSection("crafts");
    if (craftsSection == null)
      return;

    for (String craftId : craftsSection.getKeys(false)) {
      ConfigurationSection craft = craftsSection.getConfigurationSection(craftId);
      if (craft != null) {
        registerRecipe(craftId, craft);
      }
    }
  }

  private void registerRecipe(String craftId, ConfigurationSection craft) {
    // Récupération du résultat
    String resultMaterial = craft.getString("result.material");
    int resultAmount = craft.getInt("result.amount", 1);

    if (resultMaterial == null)
      return;

    Material material = Material.getMaterial(resultMaterial.toUpperCase());
    if (material == null)
      return;

    ItemStack result = new ItemStack(material, resultAmount);
    ItemMeta meta = result.getItemMeta();

    // Apply custom name
    if (craft.contains("result.name")) {
      String name = craft.getString("result.name");
      if (name != null) {
        meta.setDisplayName(name.replace('&', '§'));
      }
    }

    // Apply custom lore
    if (craft.contains("result.lore")) {
      List<String> lore = new ArrayList<>();
      for (String line : craft.getStringList("result.lore")) {
        lore.add(line.replace('&', '§'));
      }
      meta.setLore(lore);
    }

    // Apply enchantments
    if (craft.contains("result.enchantments")) {
      ConfigurationSection enchantments = craft.getConfigurationSection("result.enchantments");
      if (enchantments != null) {
        for (String enchantKey : enchantments.getKeys(false)) {
          try {
            Enchantment enchantment = Enchantment.getByName(enchantKey.toUpperCase());
            if (enchantment != null) {
              int level = enchantments.getInt(enchantKey);
              meta.addEnchant(enchantment, level, true);
            } else {
              getLogger().warning("Enchantement inconnu: " + enchantKey + " dans le craft " + craftId);
            }
          } catch (Exception e) {
            getLogger().warning("Erreur avec l'enchantement " + enchantKey + ": " + e.getMessage());
          }
        }
      }
    }

    // Apply attributes
    if (craft.contains("result.attributes")) {
      ConfigurationSection attributes = craft.getConfigurationSection("result.attributes");
      if (attributes != null) {
        for (String attrKey : attributes.getKeys(false)) {
          double value = attributes.getDouble(attrKey);

          Attribute attribute = null;
          EquipmentSlot slot = EquipmentSlot.HAND;

          // Map attribute names to Bukkit Attributes
          switch (attrKey.toLowerCase()) {
            case "attack_damage":
            case "generic.attack_damage":
              attribute = Attribute.GENERIC_ATTACK_DAMAGE;
              break;
            case "attack_speed":
            case "generic.attack_speed":
              attribute = Attribute.GENERIC_ATTACK_SPEED;
              break;
            case "max_health":
            case "generic.max_health":
              attribute = Attribute.GENERIC_MAX_HEALTH;
              break;
            case "movement_speed":
            case "generic.movement_speed":
              attribute = Attribute.GENERIC_MOVEMENT_SPEED;
              slot = EquipmentSlot.FEET;
              break;
            case "armor":
            case "generic.armor":
              attribute = Attribute.GENERIC_ARMOR;
              slot = EquipmentSlot.CHEST;
              break;
            case "armor_toughness":
            case "generic.armor_toughness":
              attribute = Attribute.GENERIC_ARMOR_TOUGHNESS;
              slot = EquipmentSlot.CHEST;
              break;
            case "luck":
            case "generic.luck":
              attribute = Attribute.GENERIC_LUCK;
              break;
            case "knockback_resistance":
            case "generic.knockback_resistance":
              attribute = Attribute.GENERIC_KNOCKBACK_RESISTANCE;
              slot = EquipmentSlot.CHEST;
              break;
            default:
              getLogger().warning("Attribut inconnu: " + attrKey + " dans le craft " + craftId);
              continue;
          }

          if (attribute != null) {
            // Generate a unique UUID based on craftId and attribute name
            UUID uuid = generateUUID(craftId, attrKey);
            AttributeModifier modifier = new AttributeModifier(
                uuid,
                attrKey,
                value,
                AttributeModifier.Operation.ADD_NUMBER,
                slot);
            meta.addAttributeModifier(attribute, modifier);
          }
        }
      }
    }

    // Apply unbreakable
    if (craft.getBoolean("result.unbreakable", false)) {
      meta.setUnbreakable(true);
      meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
    }

    // Hide enchantments and attributes by default
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

    result.setItemMeta(meta);

    // Create recipe
    NamespacedKey key = new NamespacedKey(this, "unique_craft_" + craftId);
    ShapedRecipe recipe = new ShapedRecipe(key, result);

    // Configuration de la forme
    List<String> shapeList = craft.getStringList("shape");
    if (shapeList.size() != 3) {
      getLogger().warning("La forme du craft " + craftId + " doit avoir 3 lignes!");
      return;
    }

    recipe.shape(shapeList.toArray(new String[0]));

    // Configuration des ingrédients
    ConfigurationSection ingredients = craft.getConfigurationSection("ingredients");
    if (ingredients != null) {
      for (String ingredientKey : ingredients.getKeys(false)) {
        char symbol = ingredientKey.charAt(0);
        String ingredientMaterial = ingredients.getString(ingredientKey);
        if (ingredientMaterial != null) {
          Material ingMaterial = Material.getMaterial(ingredientMaterial.toUpperCase());
          if (ingMaterial != null) {
            recipe.setIngredient(symbol, ingMaterial);
          } else {
            getLogger().warning("Matériau inconnu: " + ingredientMaterial + " dans le craft " + craftId);
          }
        }
      }
    }

    // Enregistrement de la recette
    try {
      Bukkit.addRecipe(recipe);
      getLogger().info("Craft enregistré: " + craftId);
    } catch (IllegalArgumentException e) {
      getLogger().warning("Erreur avec le craft " + craftId + ": " + e.getMessage());
    }
  }

  // Helper method to generate UUIDs for attributes
  private UUID generateUUID(String craftId, String attributeName) {
    // Use a consistent method to generate UUIDs from craftId and attribute name
    String combined = craftId + "_" + attributeName;
    int hash = combined.hashCode();

    // Create a UUID from the hash (ensuring it's unique per craft+attribute)
    return new UUID(
        ((long) craftId.hashCode() << 32) | (hash & 0xffffffffL),
        ((long) attributeName.hashCode() << 32) | (System.currentTimeMillis() & 0xffffffffL));
  }

  @EventHandler
  public void onPrepareCraft(PrepareItemCraftEvent event) {
    Recipe recipe = event.getRecipe();
    if (recipe == null)
      return;

    // Vérifier si c'est une de nos recettes uniques
    String craftId = getCraftIdFromRecipe(recipe);
    if (craftId == null)
      return;

    // Vérifier si cet objet a déjà été crafté globalement
    if (globalCraftedItems.contains(craftId)) {
      event.getInventory().setResult(null);
      if (event.getViewers().get(0) instanceof Player) {
        Player player = (Player) event.getViewers().get(0);
        player.sendMessage("§cCet objet a déjà été crafté sur le serveur !");
      }
    }
  }

  @EventHandler
  public void onCraftItem(CraftItemEvent event) {
    if (event.getWhoClicked() instanceof Player) {
      Player player = (Player) event.getWhoClicked();
      Recipe recipe = event.getRecipe();

      if (recipe == null)
        return;

      String craftId = getCraftIdFromRecipe(recipe);
      if (craftId == null)
        return;

      // Vérifier si l'objet a déjà été crafté
      if (globalCraftedItems.contains(craftId)) {
        event.setCancelled(true);
        player.sendMessage("§cCet objet a déjà été crafté sur le serveur !");
        return;
      }

      // Marquer comme crafté
      globalCraftedItems.add(craftId);

      // Enregistrer dans la config
      craftsConfig.set("crafted." + craftId, true);
      saveCraftsConfig();

      // Envoyer le message
      ConfigurationSection craftsSection = craftsConfig.getConfigurationSection("crafts." + craftId);
      if (craftsSection != null) {
        String message = craftsSection.getString("message",
            "§aL'objet unique a été crafté par " + player.getName() + " !");
        String finalMessage = message.replace("%player%", player.getName());

        // Broadcast message to all players
        for (Player p : Bukkit.getOnlinePlayers()) {
          p.sendMessage(finalMessage);
        }
      }

      player.sendMessage("§aVous avez crafté un objet unique !");
    }
  }

  private String getCraftIdFromRecipe(Recipe recipe) {
    if (recipe instanceof ShapedRecipe) {
      ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
      String key = shapedRecipe.getKey().getKey();
      if (key.startsWith("unique_craft_")) {
        return key.substring("unique_craft_".length());
      }
    }
    return null;
  }

  // Commande pour gérer les crafts
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equalsIgnoreCase("uniquecraft")) {
      if (args.length == 0) {
        sender.sendMessage("§6=== UniqueCrafts ===");
        sender.sendMessage("§e/uniquecraft reload - Recharge la configuration");
        sender.sendMessage("§e/uniquecraft list - Liste les crafts disponibles");
        sender.sendMessage("§e/uniquecraft reset <id> - Réinitialise un craft");
        return true;
      }

      if (args[0].equalsIgnoreCase("reload")) {
        if (!sender.hasPermission("uniquecrafts.reload")) {
          sender.sendMessage("§cVous n'avez pas la permission !");
          return true;
        }

        reloadConfig();
        loadCraftsConfig();
        globalCraftedItems.clear();
        loadCraftedItems();

        // Recharger les recettes
        removeAllRecipes();
        loadCustomRecipes();

        sender.sendMessage("§aConfiguration rechargée !");
        return true;
      }

      if (args[0].equalsIgnoreCase("list")) {
        ConfigurationSection craftsSection = craftsConfig.getConfigurationSection("crafts");
        if (craftsSection != null) {
          sender.sendMessage("§6=== Crafts disponibles ===");
          for (String craftId : craftsSection.getKeys(false)) {
            boolean crafted = globalCraftedItems.contains(craftId);
            String status = crafted ? "§cDéjà crafté" : "§aDisponible";
            sender.sendMessage("§e- " + craftId + ": " + status);
          }
        } else {
          sender.sendMessage("§cAucun craft configuré !");
        }
        return true;
      }

      if (args[0].equalsIgnoreCase("reset")) {
        if (!sender.hasPermission("uniquecrafts.reset")) {
          sender.sendMessage("§cVous n'avez pas la permission !");
          return true;
        }

        if (args.length < 2) {
          sender.sendMessage("§cUsage: /uniquecraft reset <craft_id>");
          return true;
        }

        String craftId = args[1];
        if (!globalCraftedItems.contains(craftId)) {
          sender.sendMessage("§cCe craft n'a pas encore été réalisé !");
          return true;
        }

        globalCraftedItems.remove(craftId);
        craftsConfig.set("crafted." + craftId, null);
        saveCraftsConfig();
        sender.sendMessage("§aCraft " + craftId + " réinitialisé !");
        return true;
      }
    }
    return false;
  }

  private void removeAllRecipes() {
    // Get all recipes and remove ours
    Iterator<Recipe> iterator = Bukkit.recipeIterator();
    List<Recipe> newRecipes = new ArrayList<>();

    while (iterator.hasNext()) {
      Recipe recipe = iterator.next();
      if (recipe instanceof ShapedRecipe) {
        ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
        // Keep recipes that are NOT from our plugin
        if (!shapedRecipe.getKey().getNamespace().equals(getName().toLowerCase())) {
          newRecipes.add(recipe);
        }
      } else {
        // Keep non-shaped recipes
        newRecipes.add(recipe);
      }
    }

    // Clear and re-add all recipes
    Bukkit.clearRecipes();
    for (Recipe recipe : newRecipes) {
      Bukkit.addRecipe(recipe);
    }
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (command.getName().equalsIgnoreCase("uniquecraft")) {
      if (args.length == 1) {
        List<String> completions = new ArrayList<>();
        for (String option : Arrays.asList("reload", "list", "reset")) {
          if (option.startsWith(args[0].toLowerCase())) {
            completions.add(option);
          }
        }
        return completions;
      } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
        ConfigurationSection craftsSection = craftsConfig.getConfigurationSection("crafts");
        if (craftsSection != null) {
          List<String> completions = new ArrayList<>();
          for (String craftId : craftsSection.getKeys(false)) {
            if (craftId.startsWith(args[1].toLowerCase())) {
              completions.add(craftId);
            }
          }
          return completions;
        }
      }
    }
    return new ArrayList<>();
  }
}
