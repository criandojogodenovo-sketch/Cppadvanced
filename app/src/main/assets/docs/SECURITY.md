# Segurança

## Princípios aplicados no código

1. **Path traversal**: toda a operação de ficheiros passa por `SafePath`, que
   normaliza e garante que o destino permanece dentro da raiz autorizada
   (projetos da app ou pasta escolhida via SAF). Testes unitários cobrem os
   casos `../`, caminhos absolutos e normes nulos.
2. **ZIP seguro**: `ZipUtils` recusa entradas com `..`, caminhos absolutos,
   mais de 10 000 entradas ou descomprimidos > 512 MB (zip-bomb).
3. **Operações destrutivas**: eliminar ficheiros/pastas exige confirmação com
   o nome escrito pelo utilizador visível no diálogo.
4. **Execução de código**: nada é executado sem ação explícita do utilizador.
   Nesta build não há execução local alguma (ver `docs/TOOLCHAIN-STRATEGY.md`).
5. **Credenciais**: nenhum token, password ou keystore no código. Builds de
   release assinadas apenas via segredos do GitHub (`docs/BUILD.md`).
6. **Permissões Android mínimas**: a app não pede nenhuma permissão perigosa.
   Projetos vivem no armazenamento privado da app; pastas externas acedidas
   apenas via SAF (DocumentFile) com permissões persistidas.
7. **Recuperação de dados**: autosave + ficheiros de recuperação; gravação
   atómica (ficheiro temporário + rename) para evitar corrupção.
8. **Logs sem segredos**: os registos de build nunca incluem credenciais.

## Nota sobre tokens GitHub

Se partilhaste um token de acesso pessoal num chat, email ou screenshot,
considera-o comprometido: **revoga-o** (GitHub → Settings → Developer settings →
Personal access tokens) e cria um novo. Tokens usados por ferramentas devem ter
o âmbito mínimo (ex.: apenas `repo` para o repo do projeto, ou fine-grained com
acesso só a este repositório) e data de validade curta.
