package Nik;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {

    public static void main(String args[]) throws Exception {
        System.out.println("Ready to receive the file!");
        final int port = 5678;
        final String fileName = "cat1.jpg";

        receiveAndCreate(port, fileName);
    }

    public static void receiveAndCreate(int port, String fileName) throws IOException {
        // создание сокета, адреса и файла для для получения
        DatagramSocket socket = new DatagramSocket(port);
        InetAddress address;
        File file = new File(fileName);
        FileOutputStream outToFile = new FileOutputStream(file);

        // флаг для индикации последнего сообщения
        boolean lastMessageFlag = false;

        // порядковый номер
        int sequenceNumber = 0;
        int lastSequenceNumber = 0;

        while (!lastMessageFlag) {
            // создание байтового массива для пакета и для данных без заголовка
            byte[] message = new byte[1024];
            byte[] fileByteArray = new byte[1021];

            // получение пакета и извлечение данных
            DatagramPacket receivedPacket = new DatagramPacket(message, message.length);
            socket.setSoTimeout(0);
            socket.receive(receivedPacket);
            message = receivedPacket.getData();

            // получение порта и адреса для отправления подтверждения
            address = receivedPacket.getAddress();
            port = receivedPacket.getPort();

            // получение последовательного номера
            sequenceNumber = ((message[0] & 0xff) << 8) + (message[1] & 0xff);

            // проверка на флаг последнего сообщения
            if ((message[2] & 0xff) == 1) {
                lastMessageFlag = true;
            } else {
                lastMessageFlag = false;
            }

            if (sequenceNumber == (lastSequenceNumber + 1)) {

                // изменение последнего номера пакета
                lastSequenceNumber = sequenceNumber;

                // извлечение данных из пакета
                for (int i=3; i < 1024 ; i++) {
                    fileByteArray[i-3] = message[i];
                }

                // запись данных в файл
                outToFile.write(fileByteArray);
                System.out.println("Received: Sequence number = " + sequenceNumber +", Flag = " + lastMessageFlag);

                // отправка подтвеждения
                sendAck(lastSequenceNumber, socket, address, port);

                // проверка на последнее сообщение
                if (lastMessageFlag) {
                    outToFile.close();
                }
            } else {
                // если пакет получен, то снова посылаем номер для этого пакета
                if (sequenceNumber < (lastSequenceNumber + 1)) {
                    sendAck(sequenceNumber, socket, address, port);
                } else {
                    // перепосылаем номер для последнего полученного пакета
                    sendAck(lastSequenceNumber, socket, address, port);
                }
            }
        }

        socket.close();
        System.out.println("File " + fileName + " has been received.");
    }

    public static void sendAck(int lastSequenceNumber, DatagramSocket socket, InetAddress address, int port) throws IOException {
        // переотправка подтверждения
        byte[] ackPacket = new byte[2];
        ackPacket[0] = (byte)(lastSequenceNumber >> 8);
        ackPacket[1] = (byte)(lastSequenceNumber);
        DatagramPacket acknowledgement = new  DatagramPacket(ackPacket, ackPacket.length, address, port);
        socket.send(acknowledgement);
        System.out.println("Sent ack: Sequence Number = " + lastSequenceNumber);
    }
}
