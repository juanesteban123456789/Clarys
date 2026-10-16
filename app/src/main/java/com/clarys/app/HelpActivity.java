package com.clarys.app;

import android.os.Bundle;
import android.widget.TextView;
import com.clarys.app.data.SupabaseStore;

public class HelpActivity extends BaseScreenActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        boolean privacy=getIntent().getBooleanExtra("privacy",false);
        ((TextView)findViewById(R.id.textHelpTitle)).setText(privacy ? "Privacidad y datos personales" : "Ayuda y contacto");
        ((TextView)findViewById(R.id.textHelpBody)).setText(privacy
                ? "Clarys permite consultar prendas sin crear una cuenta. Al enviar un pedido se solicita nombre y WhatsApp "
                +"para que el taller gestione la solicitud. El taller destinatario puede consultar esos datos y los artículos pedidos.\n\n"
                +"La administración utiliza Supabase Auth para correo o Google. Clarys no obtiene tu contraseña de Google. "
                +"Los datos e imágenes se almacenan en Supabase; las notas internas y comprobantes se restringen al taller.\n\n"
                +"El carrito, favoritos y códigos de seguimiento se conservan en este dispositivo. Los datos de contacto "
                +"y seguimiento se almacenan cifrados. Si borras los datos o desinstalas la aplicación, puedes perder el seguimiento local.\n\n"
                +"Puedes solicitar al taller la corrección o eliminación de tus datos a través del WhatsApp de su producto. "
                +"Las cuentas administrativas pueden solicitar su eliminación desde Configuración. "
                +"Los pedidos y datos comerciales se usan para gestionar el servicio, no para publicidad."
                : "CATÁLOGO\nExplora categorías, filtra prendas y selecciona talla y color cuando el producto tenga variantes. "
                +"Los favoritos y el carrito quedan guardados en tu dispositivo.\n\n"
                +"PEDIDOS\nCada solicitud pertenece a un taller. Revisa el total y el contacto antes de enviar. "
                +"El envío es una solicitud: el taller debe confirmar existencias, pago y entrega. "
                +"Consulta el progreso desde Mis pedidos.\n\n"
                +"ADMINISTRACIÓN\nConfirmar un pedido reserva existencias. Cancelarlo libera la reserva. "
                +"Entregarlo registra la venta y descuenta el inventario. Las tallas y colores se gestionan desde el producto.\n\n"
                +"CONTACTO\nPara dudas sobre una prenda, utiliza Consultar en el detalle del producto. "
                +"Para errores de acceso, utiliza recuperación de contraseña en la pantalla administrativa.\n\n"
                +"AVISOS\nLos avisos de pedidos pueden retrasarse según la batería, la conexión o los permisos del dispositivo.");
        ((TextView)findViewById(R.id.textHelpVersion)).setText(
                (BuildConfig.IS_ADMIN_APP ? "Clarys Gestión" : "Clarys")+" · versión "+BuildConfig.VERSION_NAME);
        findViewById(R.id.buttonHelpBack).setOnClickListener(view -> navigateHome());
    }
    @Override protected boolean isPublicScreen() { return true; }
}
