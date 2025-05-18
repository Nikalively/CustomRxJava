# Пользовательская реализация RxJava

Пользовательская реализация концепций реактивного программирования, аналогичная RxJava. Цель — получить компактный, но при этом полнофункциональный каркас для построения асинхронных потоков данных, включающий управление потоками выполнения, обработку ошибок, базовые и составные операторы, а также механизм отмены подписки.

## Содержание
1. [Архитектура](#архитектура)
2. [Основные компоненты](#основные-компоненты)
3. [Планировщики (Schedulers)](#планировщики-schedulers)
4. [Операторы](#операторы)
5. [Обработка ошибок](#обработка-ошибок)
6. [Тестирование](#тестирование)
7. [Примеры использования](#примеры-использования)

## Архитектура

Библиотека строится на классическом паттерне «Наблюдатель» (Observer Pattern) и включает в себя:

- Источники данных (`Observable<T>`), которые генерируют элементы и отправляют их подписчикам.
- Приёмники (`Observer<T>`), получающие элементы, ошибки и уведомления о завершении.
- Механизмы управления подписками (интерфейс `Disposable`).
- Планировщики (`Scheduler`) для выполнения операций в нужных потоках.
- Операторы, обогащающие, трансформирующие или фильтрующие поток.

Вся передача событий происходит через цепочку `Observable → Операторы → Observer`. Каждый оператор «оборачивает» предыдущий, сохраняя реактивный контракт.

---

## Основные компоненты

### Интерфейс Observer<T>
Определяет контракт подписчика, получающего три типа сигналов:
- `void onNext(T item)` — следующий элемент потока.
- `void onError(Throwable t)` — при возникновении непредвиденной ситуации.
- `void onComplete()` — поток завершён, новых элементов не будет.

### Класс Observable<T>
Сердце библиотеки, отвечает за:
- Создание источника через метод `create(OnSubscribe<T> source)`.
- Публикацию данных подписчикам в том порядке, как они были сгенерированы.
- Оборачивание цепочки операторов, сетевых или вычислительных планировщиков.

Пример создания:
```java
Observable<String> src = Observable.create(observer -> {
    observer.onNext("Hello");
    observer.onNext("World");
    observer.onComplete();
});
```
### Подписка и Disposable<T>
При вызове subscribe(...) мы получаем объект Disposable:
- dispose() — прекращает получение новых событий.
- isDisposed() — проверяет, была ли отмена.

Подписка запускает OnSubscribe.call(), а все сигналы (onNext, onError, onComplete) маршрутизируются по подписчику до тех пор, пока не будет вызван dispose() или onError/onComplete.
```java
Disposable subscription = observable.subscribe(
        item -> System.out.println("Получено: " + item),
        error -> System.out.println("Ошибка: " + error.getMessage()),
        () -> System.out.println("Завершено!")
);
```
### Планировщики (Schedulers)
Для контроля, где именно выполняются генерация и обработка событий, реализованы три типа планировщиков:

1. IOThreadScheduler:
- Использует Executors.newCachedThreadPool().
- Оптимален для не-CUP-ориентированных задач: работа с сетью, файловые операции.
- Динамически масштабируется: создает новые потоки при пике нагрузки и реиспользует их после простоя.

2. ComputationScheduler:
- Использует Executors.newFixedThreadPool(n), где n = Runtime.getRuntime().availableProcessors().
- Идеален для CPU-интенсивных задач: математические или алгоритмические расчёты.
- Фиксированный размер пула предотвращает избыточную конкуренцию за CPU.

3. SingleThreadScheduler:
- Исполнитель с единственным потоком (newSingleThreadExecutor()).
- Гарантирует полную последовательность выполнения в одном потоке.
- Применим для операций, требующих строгого порядка или при обновлении UI.

### Методы переключения потоков
- subscribeOn(Scheduler sched) — указывает, в каком потоке запустить подписку и эмиссию onSubscribe.call().
- observeOn(Scheduler sched) — перенаправляет все дальнейшие сигналы (onNext, onError, onComplete) в указанный планировщик.

### Операторы
***map(Function<? super T, ? extends R> mapper)***

Преобразует каждый элемент исходного потока по заданной функции:
```java
observable.map(x -> x * 10)
        .subscribe(System.out::println);
```
***filter(Predicate<? super T> predicate)***

Пропускает дальше только те элементы, которые удовлетворяют условию:
```java
observable.filter(x -> x % 2 == 0)
        .subscribe(System.out::println);
```
***flatMap(Function<? super T, Observable> mapper)***

Для каждого входного элемента запускает вложенный Observable<R> и объединяет их результаты в один поток.
Особенности реализации:
- Внутренние onNext буферизуются.
- При возникновении onError внутри любого внутреннего потока — буфер очищается и ошибка немедленно передаётся дальше, без эмиссии накопленных элементов.
- При успешном завершении внутреннего потока буфер «смывается» в выходной поток.
```java
observable.flatMap(id -> Observable.create(obs -> {
        obs.onNext(fetchData(id));
        obs.onComplete();
}))
        .subscribe(System.out::println);
```
### Обработка ошибок
Реактивный поток гарантирует:
- Если на любом этапе (источник или оператор) бросается исключение — оно передаётся в onError подписчика.
- После onError любая дальнейшая эмиссия прекращается.
- В случае комбинирования операторов (map, filter, flatMap) ошибка доходит до подписчика без «поглощения».
- Если подписка отменена (dispose()), сигналы игнорируются.

### Тестирование
Проект включает обширные модульные тесты, охватывающие:
- Базовую функциональность Observable.
- Операторы map, filter, flatMap.
- Поведение планировщиков.
- Обработку ошибок и отмену подписки.

### Категории тестов
- ***ObservableTest*** — базовые сценарии, ошибки, операторы.
- ***OperatorChainTest*** — цепочки операторов и flatMap.
- ***ConcurrencyTest, SchedulerIntegrationTest*** — проверка Schedulers и многопоточности.
- ***AdvancedScenariosTest*** — сложные сценарии с отменой подписки и внутренними ошибками.

### Примеры использования
### Простая последовательная цепочка
```java
Observable.create(observer -> {
        observer.onNext(1);
    observer.onNext(2);
    observer.onComplete();
})
        .subscribe(
        item -> System.out.println("Получено: " + item),
error -> System.out.println("Ошибка: " + error.getMessage()),
        () -> System.out.println("Завершено!")
);
```
### Асинхронная эмиссия и обработка
```java
Observable.create(observer -> {
        observer.onNext(1);
    observer.onNext(2);
    observer.onNext(3);
    observer.onComplete();
})
        .filter(x -> x % 2 == 0)
        .map(x -> x * 2)
        .flatMap(x -> Observable.create(observer -> {
        observer.onNext(x * 10);
    observer.onNext(x * 20);
    observer.onComplete();
}))
        .subscribeOn(new IOThreadScheduler())
        .observeOn(new ComputationScheduler())
        .subscribe(
        item -> System.out.println("Результат: " + item),
error -> System.out.println("Ошибка: " + error.getMessage()),
        () -> System.out.println("Завершено!")
);
```
### FlatMap с буферизацией и отменой
```java
Disposable disp = Observable.range(1, 3)
        .flatMap(i -> Observable.create(obs -> {
            obs.onNext(i * 100);
            obs.onError(new RuntimeException("Ошибка для " + i));
        }))
        .subscribe(
                System.out::println,
                err -> System.err.println("Поймали: " + err.getMessage())
        );

// Отменяем подписку через 50 мс
Thread.sleep(50);
disp.dispose();
```
