db.usuarios.updateOne({email:'admin1@demo.com'}, {'$set':{email_verificado:false, codigoVerificacion:'123456', codigoVerificacionExpira:new Date(Date.now()+600000)}});
