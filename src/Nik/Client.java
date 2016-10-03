package Nik;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Vector;

public class Client {

    public static void main(String args[]) throws Exception {
        // указание адреса, хостнейма и имени файла для отправки
        final String hostName = "localhost";
        final int port = 5678;
        final String fileName = "cat.jpg";

        createAndSend(hostName, port, fileName);
    }

    public static void createAndSend(String hostName, int port, String fileName) throws IOException {
        System.out.println("Sending the file");

        // создание сокета, адреса и файла для отправки
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(hostName);
        File file = new File(fileName);

        // создание байтового массива для файла
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int)file.length()];
        inFromFile.read(fileByteArray);

        // создание пременных для флага последнего сообщения и последовательного номера
        int sequenceNumber = 0;
        boolean lastMessageFlag = false;

        // флаг для последнего подтвержденного пакета и последовательный номер
        int ackSequenceNumber = 0;
        int lastAckedSequenceNumber = 0;
        boolean lastAcknowledgedFlag = false;

        // счетчик повторных передач и размер окна
        int retransmissionCounter = 0;
        int windowSize = 128;

        // вектор для хранения переданных сообщений
        Vector <byte[]> sentMessageList = new Vector <byte[]>();


        for (int i=0; i < fileByteArray.length; i = i+1021 ) {

            sequenceNumber += 1;

            byte[] message = new byte[1024];

            // установка первых двух байт для последовательного номера
            message[0] = (byte)(sequenceNumber >> 8);
            message[1] = (byte)(sequenceNumber);

            // если пакет последний, то устанавливаем 1 в 3 байт пакета
            if ((i+1021) >= fileByteArray.length) {
                lastMessageFlag = true;
                message[2] = (byte)(1);
            } else { // иначе 3 байт в 0
                lastMessageFlag = false;
                message[2] = (byte)(0);
            }

            // копирование байт массива файла в массив для пакета
            if (!lastMessageFlag) {
                for (int j=0; j != 1021; j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }
            else if (lastMessageFlag) { // если последний пакет
                for (int j=0;  j < (fileByteArray.length - i); j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }

            // создание пакета
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, port);

            // добавление данных пакета в вектор
            sentMessageList.add(message);

            while (true) {
                // если след последов номер выходит за рамки окна
                if ((sequenceNumber - windowSize) > lastAckedSequenceNumber) {

                    boolean ackRecievedCorrect = false;
                    boolean ackPacketReceived = false;

                    while (!ackRecievedCorrect) {
                        // проверка подтверждения
                        byte[] ack = new byte[2];
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                        try {
                            socket.setSoTimeout(50);
                            socket.receive(ackpack);
                            ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                            ackPacketReceived = true;
                        } catch (SocketTimeoutException e) {
                            ackPacketReceived = false;
                        }

                        if (ackPacketReceived) {
                            if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                                lastAckedSequenceNumber = ackSequenceNumber;
                            }
                            ackRecievedCorrect = true;
                            System.out.println("Ack recieved: Sequence Number = " + ackSequenceNumber);
                            break; 	// после подтерждения можно посылать следующий пакет
                        } else { // переотправить пакет
                            System.out.println("Resending: Sequence Number = " + sequenceNumber);
                            // переотправить пакет следующий за последним подтвержденным пакетом и так далее
                            for (int y=0; y != (sequenceNumber - lastAckedSequenceNumber); y++) {
                                byte[] resendMessage = new byte[1024];
                                resendMessage = sentMessageList.get(y + lastAckedSequenceNumber);

                                DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                                socket.send(resendPacket);
                                retransmissionCounter += 1;
                            }
                        }
                    }
                } else { // если не выходит за рамки, то выходит для отравки пакета
                    break;
                }
            }

            // отправка пакета
            socket.send(sendPacket);
            System.out.println("Sent: Sequence number = " + sequenceNumber + ", Flag = " + lastMessageFlag);


            // проверка на подтверждение
            while (true) {
                boolean ackPacketReceived = false;
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(10);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    ackPacketReceived = false;
                    break;
                }

                if (ackPacketReceived) {
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                        System.out.println("Ack recieved: Sequence number = " + ackSequenceNumber);
                    }
                }
            }
        }

        // продолжаем проверять и отправлять, пока не поулчим финальное подтверждение
        while (!lastAcknowledgedFlag) {

            boolean ackRecievedCorrect = false;
            boolean ackPacketReceived = false;

            while (!ackRecievedCorrect) {

                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    ackPacketReceived = false;
                }

                // если последний пакет
                if (lastMessageFlag) {
                    lastAcknowledgedFlag = true;
                    break;
                }
                // выходим, если получили подтверждение и приступаем к следующему пакету
                if (ackPacketReceived) {
                    System.out.println("Ack recieved: Sequence number = " + ackSequenceNumber);
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                    }
                    ackRecievedCorrect = true;
                    break;
                } else { // переотправить пакет
                    // переотправить пакет следующий за последний падвержденным пакетом и так далее
                    for (int j=0; j != (sequenceNumber-lastAckedSequenceNumber); j++) {
                        byte[] resendMessage = new byte[1024];
                        resendMessage = sentMessageList.get(j + lastAckedSequenceNumber);
                        DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                        socket.send(resendPacket);
                        System.out.println("Resending: Sequence Number = " + lastAckedSequenceNumber);

                        // квеличение счетчика переотправлений
                        retransmissionCounter += 1;
                    }
                }
            }
        }

        socket.close();
        System.out.println("File " + fileName + " has been sent");

        System.out.println("Number of retransmissions: " + retransmissionCounter);
    }
}