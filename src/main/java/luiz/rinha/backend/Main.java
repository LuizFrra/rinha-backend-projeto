package luiz.rinha.backend;

import io.javalin.Javalin;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.useVirtualThreads = true;
                }).post(AddTransactionUseCase.resource, AddTransactionUseCase::execute)
                .get(GetTransactionUseCase.resource, GetTransactionUseCase::execute)
                .start(8080);
    }
}