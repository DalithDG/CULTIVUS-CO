const result = db.pedidos.deleteMany({ vendedor_id: { $exists: false } });
print(`Se eliminaron ${result.deletedCount} pedidos antiguos incompatibles.`);
