package io.conceptive.netplan.metrics.util;

import io.reactivex.*;
import io.reactivex.disposables.Disposable;
import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * Contains all utilitiy methods for a shell command
 *
 * @author w.glanzer, 18.09.2020
 */
public class ShellCommand
{

  /**
   * Executes a given command and returns its output
   *
   * @param pCommand Command to execute
   * @return the resulting string
   */
  @NotNull
  public static Flowable<String> executeCommand(@NotNull String pCommand)
  {
    return Flowable.create(new _Command(pCommand), BackpressureStrategy.LATEST);
  }

  /**
   * Command
   */
  private static class _Command implements FlowableOnSubscribe<String>
  {
    private final String command;
    private Process process;

    public _Command(@NotNull String pCommand)
    {
      command = pCommand;
    }

    @Override
    public void subscribe(@NotNull FlowableEmitter<String> pEmitter) throws Exception
    {
      pEmitter.setDisposable(new Disposable()
      {
        @Override
        public void dispose()
        {
          if(process != null)
          {
            process.destroy();
            process = null;
          }
        }

        @Override
        public boolean isDisposed()
        {
          return process != null && process.isAlive();
        }
      });

      // execute
      Process p = Runtime.getRuntime().exec(command);
      try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())))
      {
        String line;
        while ((line = reader.readLine()) != null)
          pEmitter.onNext(line);
      }

      // finished
      pEmitter.onComplete();
    }
  }

}