# Regras ProGuard/R8 (R8 atualmente desligado nas builds; mantido para ativação futura)
-keepattributes *Annotation*
# org.json é fornecido pela plataforma Android — manter como está
-dontwarn org.json.**
